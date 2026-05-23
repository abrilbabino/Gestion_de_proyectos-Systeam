// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/InvestmentSwap.sol";

contract MockIDEA is IERC20 {
    string public name = "IDEA";
    string public symbol = "IDEA";
    uint8 public decimals = 18;

    mapping(address => uint256) private _balances;
    mapping(address => mapping(address => uint256)) private _allowances;

    function totalSupply() external view returns (uint256) { return 1_000_000e18; }
    function balanceOf(address account) external view returns (uint256) { return _balances[account]; }
    function allowance(address owner, address spender) external view returns (uint256) { return _allowances[owner][spender]; }

    function transfer(address to, uint256 amount) external returns (bool) {
        _balances[msg.sender] -= amount;
        _balances[to] += amount;
        return true;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        _allowances[msg.sender][spender] = amount;
        return true;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        _allowances[from][msg.sender] -= amount;
        _balances[from] -= amount;
        _balances[to] += amount;
        return true;
    }

    function mint(address to, uint256 amount) external {
        _balances[to] += amount;
    }
}

contract InvestmentSwapTest is Test {
    InvestmentSwap public swap;
    MockIDEA public idea;
    address public treasury;
    address public owner;
    address public alice;
    address public bob;

    uint256 constant PROYECTO_ID = 1;
    string constant NOMBRE = "Test Project";
    string constant SIMBOLO = "TST";
    uint256 constant SUPPLY = 1000;
    uint256 constant IDEA_AMOUNT = 100e18;
    uint256 constant SUBTOKEN_AMOUNT = 10;

    function setUp() public {
        owner = address(this);
        treasury = makeAddr("treasury");
        alice = makeAddr("alice");
        bob = makeAddr("bob");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);

        idea.mint(alice, 1000e18);
        vm.prank(alice);
        idea.approve(address(swap), 1000e18);
    }

    function test_CrearTokenProyecto() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        assertEq(swap.tokenDeProyecto(PROYECTO_ID), tokenAddr);
        assertTrue(tokenAddr != address(0));

        uint256 treasuryBalance = ProjectToken(tokenAddr).balanceOf(treasury);
        assertEq(treasuryBalance, SUPPLY);
    }

    function test_CrearTokenProyectoSinSupply() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 0);

        uint256 treasuryBalance = ProjectToken(tokenAddr).balanceOf(treasury);
        assertEq(treasuryBalance, 0);
    }

    function test_CrearTokenProyectoSoloOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        swap.crearTokenProyecto(999, "Hack", "HCK", 0);
    }

    function test_CrearTokenProyectoDuplicadoRevert() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.expectRevert();
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
    }

    function test_Invest() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);

        uint256 aliceBalanceBefore = ProjectToken(tokenAddr).balanceOf(alice);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        uint256 aliceBalanceAfter = ProjectToken(tokenAddr).balanceOf(alice);
        assertEq(aliceBalanceAfter - aliceBalanceBefore, SUBTOKEN_AMOUNT);
        assertEq(idea.balanceOf(treasury), IDEA_AMOUNT);
    }

    function test_InvestInversorDistinto() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, bob);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(bob), SUBTOKEN_AMOUNT);
    }

    function test_InvestSinTokenRevert() public {
        vm.prank(alice);
        vm.expectRevert();
        swap.invest(999, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);
    }

    function test_InvestCeroAmountRevert() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(alice);
        vm.expectRevert();
        swap.invest(PROYECTO_ID, 0, SUBTOKEN_AMOUNT, alice);

        vm.prank(alice);
        vm.expectRevert();
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 0, alice);
    }

    function test_InvestInversorZeroRevert() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(alice);
        vm.expectRevert();
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, address(0));
    }

    function test_InvestConFaltaDeAllowance() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(bob);
        vm.expectRevert();
        swap.invest(PROYECTO_ID, 1, 1, bob);
    }

    function test_Refund() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        uint256 aliceBalanceBefore = ProjectToken(tokenAddr).balanceOf(alice);
        assertEq(aliceBalanceBefore, SUBTOKEN_AMOUNT);

        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, alice);

        uint256 aliceBalanceAfter = ProjectToken(tokenAddr).balanceOf(alice);
        assertEq(aliceBalanceAfter, 0);
    }

    function test_RefundSoloOwner() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        vm.prank(alice);
        vm.expectRevert();
        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, alice);
    }

    function test_RefundCeroRevert() public {
        vm.prank(owner);
        vm.expectRevert();
        swap.refund(PROYECTO_ID, 0, alice, alice);
    }

    function test_RefundTokenNoDesplegadoRevert() public {
        vm.expectRevert();
        swap.refund(999, 1, alice, alice);
    }

    function test_RevertDespuesDeTransferOwnership() public {
        swap.transferOwnership(alice);

        vm.prank(alice);
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(owner);
        vm.expectRevert();
        swap.crearTokenProyecto(2, "Fail", "F", 0);
    }

    function test_ObtenerTokenDeProyecto() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        assertEq(swap.obtenerTokenDeProyecto(PROYECTO_ID), tokenAddr);
    }

    function test_ObtenerTokenDeProyectoInexistente() public {
        assertEq(swap.obtenerTokenDeProyecto(999), address(0));
    }

    function test_ObtenerProyectoDeToken() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        assertEq(swap.obtenerProyectoDeToken(tokenAddr), PROYECTO_ID);
    }

    function test_ObtenerCantidadTokens() public {
        assertEq(swap.obtenerCantidadTokens(), 0);
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        assertEq(swap.obtenerCantidadTokens(), 1);
        swap.crearTokenProyecto(2, "Second", "SCD", 100);
        assertEq(swap.obtenerCantidadTokens(), 2);
    }

    function test_ObtenerTokenPorIndice() public {
        address t1 = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        address t2 = swap.crearTokenProyecto(2, "Second", "SCD", 100);
        assertEq(swap.obtenerTokenPorIndice(0), t1);
        assertEq(swap.obtenerTokenPorIndice(1), t2);
    }

    function test_ObtenerTokenPorIndiceOutOfBounds() public {
        vm.expectRevert();
        swap.obtenerTokenPorIndice(0);
    }

    function test_TransferOwnership() public {
        swap.transferOwnership(alice);
        assertEq(swap.owner(), alice);
    }

    function test_NonOwnerNoPuedeTransferirOwnership() public {
        vm.prank(alice);
        vm.expectRevert();
        swap.transferOwnership(bob);
    }

    function test_GasInvest() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);
    }

    function test_GasRefund() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);
        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, alice);
    }

    function testFuzz_InvestConDiferentesCantidades(uint8 subtokens) public {
        vm.assume(subtokens > 0 && subtokens <= 100);

        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 1000);

        uint256 ideaNeeded = IDEA_AMOUNT * subtokens / SUBTOKEN_AMOUNT;
        vm.prank(alice);
        swap.invest(PROYECTO_ID, ideaNeeded, subtokens, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), subtokens);
    }

    function testFuzz_RefundConDiferentesCantidades(uint8 subtokens) public {
        vm.assume(subtokens > 0 && subtokens <= 50);

        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 1000);

        uint256 ideaNeeded = IDEA_AMOUNT * subtokens / SUBTOKEN_AMOUNT;
        vm.prank(alice);
        swap.invest(PROYECTO_ID, ideaNeeded, subtokens, alice);

        swap.refund(PROYECTO_ID, subtokens, alice, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 0);
    }

    function testFuzz_CrearTokenConDistintoSupply(uint32 supply) public {
        vm.assume(supply <= 1_000_000);

        address tokenAddr = swap.crearTokenProyecto(uint256(supply), "Fuzz", "FUZ", supply);
        assertEq(ProjectToken(tokenAddr).totalSupply(), supply);
    }

    // ============ MULTIPLES INVERSORES ============

    function test_InvestMultiplesInversores() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        idea.mint(bob, 1000e18);
        vm.prank(bob);
        idea.approve(address(swap), 1000e18);

        vm.prank(bob);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT * 2, SUBTOKEN_AMOUNT * 2, bob);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), SUBTOKEN_AMOUNT);
        assertEq(ProjectToken(tokenAddr).balanceOf(bob), SUBTOKEN_AMOUNT * 2);
        assertEq(idea.balanceOf(treasury), IDEA_AMOUNT * 3);
    }

    function test_InvestTresInversores() public {
        address carlos = makeAddr("carlos");

        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 100000);

        idea.mint(alice, 1000e18);
        idea.mint(bob, 1000e18);
        idea.mint(carlos, 1000e18);

        vm.prank(alice);
        idea.approve(address(swap), 1000e18);
        vm.prank(bob);
        idea.approve(address(swap), 1000e18);
        vm.prank(carlos);
        idea.approve(address(swap), 1000e18);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, 50e18, 5, alice);
        vm.prank(bob);
        swap.invest(PROYECTO_ID, 30e18, 3, bob);
        vm.prank(carlos);
        swap.invest(PROYECTO_ID, 20e18, 2, carlos);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 5);
        assertEq(ProjectToken(tokenAddr).balanceOf(bob), 3);
        assertEq(ProjectToken(tokenAddr).balanceOf(carlos), 2);
        assertEq(ProjectToken(tokenAddr).totalSupply(), 100010);
    }

    // ============ REFUND PARCIAL ============

    function test_RefundParcial() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 100, alice);

        swap.refund(PROYECTO_ID, 30, alice, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 70);
    }

    function test_RefundParcialConRefundTotal() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 100, alice);

        swap.refund(PROYECTO_ID, 40, alice, alice);
        swap.refund(PROYECTO_ID, 60, alice, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 0);
    }

    function test_RefundHolderDistintoInvestor() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, bob);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 0);
    }

    // ============ REINVERSION ============

    function test_InvestDespuesDeRefund() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, alice);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), SUBTOKEN_AMOUNT);
    }

    function test_InvestDespuesDeRefundParcial() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 10000);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 100, alice);

        swap.refund(PROYECTO_ID, 30, alice, alice);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 50, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 120);
    }

    // ============ OWNERSHIP DEL PROJECT TOKEN ============

    function test_ProjectTokenOwnerEsSwap() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.owner(), address(swap));
    }

    function test_TreasuryNoPuedeMintearDirectamente() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        ProjectToken proyecto = ProjectToken(tokenAddr);
        vm.prank(treasury);
        vm.expectRevert();
        proyecto.mint(alice, 100);
    }

    // ============ SUPPLY TOTAL ============

    function test_TotalSupplyDespuesDeInvest() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        assertEq(ProjectToken(tokenAddr).totalSupply(), SUPPLY);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        assertEq(ProjectToken(tokenAddr).totalSupply(), SUPPLY + SUBTOKEN_AMOUNT);
    }

    function test_TotalSupplyDespuesDeRefund() public {
        address tokenAddr = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, SUBTOKEN_AMOUNT, alice);

        assertEq(ProjectToken(tokenAddr).totalSupply(), SUPPLY + SUBTOKEN_AMOUNT);

        swap.refund(PROYECTO_ID, SUBTOKEN_AMOUNT, alice, alice);

        assertEq(ProjectToken(tokenAddr).totalSupply(), SUPPLY);
    }

    // ============ GETTERS DESPUES DE OPERACIONES ============

    function test_GettersDespuesDeOperaciones() public {
        address t1 = swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);
        address t2 = swap.crearTokenProyecto(2, "Segundo", "SEG", 500);

        assertEq(swap.obtenerCantidadTokens(), 2);
        assertEq(swap.obtenerTokenPorIndice(0), t1);
        assertEq(swap.obtenerTokenPorIndice(1), t2);
        assertEq(swap.obtenerTokenDeProyecto(PROYECTO_ID), t1);
        assertEq(swap.obtenerTokenDeProyecto(2), t2);
        assertEq(swap.obtenerProyectoDeToken(t1), PROYECTO_ID);
        assertEq(swap.obtenerProyectoDeToken(t2), 2);
    }

    // ============ EDGE: VALORES MAXIMOS ============

    function test_InvestConCantidadMaxima() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, SUPPLY);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, 1000e18, 100, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 100);
    }

    // ============ EDGE: CREAR TOKEN CON NOMBRES EXTREMOS ============

    function test_CrearTokenNombreVacio() public {
        address tokenAddr = swap.crearTokenProyecto(99, "", "", SUPPLY);
        ProjectToken proyecto = ProjectToken(tokenAddr);
        assertEq(proyecto.name(), "");
        assertEq(proyecto.symbol(), "");
    }

    // ============ EDGE: TOKEN YA CREADO EN FACTORY ============

    function test_InvestConSubtokenAmountMayorQueSupply() public {
        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 5);

        vm.prank(alice);
        swap.invest(PROYECTO_ID, IDEA_AMOUNT, 10, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 10);
        assertEq(ProjectToken(tokenAddr).totalSupply(), 5 + 10);
    }

    // ============ FUZZ: INVEST + REFUND PARCIAL ============

    function testFuzz_InvestYRefundParcial(uint8 subtokens, uint8 refundPct) public {
        vm.assume(subtokens > 0 && subtokens <= 50);
        vm.assume(refundPct > 0 && refundPct < 100);

        swap.crearTokenProyecto(PROYECTO_ID, NOMBRE, SIMBOLO, 1000);

        uint256 ideaNeeded = IDEA_AMOUNT * subtokens / SUBTOKEN_AMOUNT;
        vm.assume(ideaNeeded <= idea.balanceOf(alice));

        vm.prank(alice);
        swap.invest(PROYECTO_ID, ideaNeeded, subtokens, alice);

        uint256 refundAmount = uint256(subtokens) * refundPct / 100;
        vm.assume(refundAmount > 0);

        swap.refund(PROYECTO_ID, refundAmount, alice, alice);

        address tokenAddr = swap.tokenDeProyecto(PROYECTO_ID);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), subtokens - refundAmount);
    }

    // ============ FUZZ: MULTIPLES PROYECTOS E INVERSORES ============

    function testFuzz_MultiplesProyectosYTokens(uint8 cantidad) public {
        vm.assume(cantidad > 0 && cantidad <= 10);

        address[] memory tokens = new address[](cantidad);

        for (uint256 i = 1; i <= cantidad; i++) {
            address t = swap.crearTokenProyecto(i, "Proy", "PRY", i * 100);
            tokens[i - 1] = t;
        }

        assertEq(swap.obtenerCantidadTokens(), cantidad);

        for (uint256 i = 0; i < cantidad; i++) {
            assertEq(swap.obtenerTokenPorIndice(i), tokens[i]);
            assertEq(swap.obtenerTokenDeProyecto(i + 1), tokens[i]);
            assertEq(swap.obtenerProyectoDeToken(tokens[i]), i + 1);
        }
    }

    receive() external payable {}
}
