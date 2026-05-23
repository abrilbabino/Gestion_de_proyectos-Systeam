// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/PaymentGateway.sol";

contract MockUSDC {
    string public name = "USD Coin";
    string public symbol = "USDC";
    uint8 public decimals = 6;

    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    function mint(address to, uint256 amount) external {
        balanceOf[to] += amount;
    }

    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }

    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }

    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract PaymentGatewayTest is Test {
    PaymentGateway public gateway;
    MockUSDC public usdc;
    address public treasury;
    address public alice;
    address public bob;

    uint256 constant MONTO = 100 * 10**6;
    bytes32 constant ACTION_ID = keccak256("pago-proyecto");

    function setUp() public {
        treasury = makeAddr("treasury");
        alice = makeAddr("alice");
        bob = makeAddr("bob");

        usdc = new MockUSDC();
        gateway = new PaymentGateway(address(usdc), treasury);

        usdc.mint(alice, 1000 * 10**6);
        vm.prank(alice);
        usdc.approve(address(gateway), 1000 * 10**6);
    }

    // ============ CONSTRUCTOR ============

    function test_ConstructorAsignaUsdc() public view {
        assertEq(address(gateway.usdc()), address(usdc));
    }

    function test_ConstructorAsignaTreasury() public view {
        assertEq(gateway.treasury(), treasury);
    }

    function test_ConstructorTreasuryEsOwner() public view {
        assertEq(gateway.owner(), treasury);
    }

    function test_ConstructorRevertSiTreasuryEsCero() public {
        vm.expectRevert();
        new PaymentGateway(address(usdc), address(0));
    }

    function test_ConstructorPermiteUsdcCero() public {
        PaymentGateway g = new PaymentGateway(address(0), bob);
        assertEq(address(g.usdc()), address(0));
    }

    // ============ PAY ============

    function test_PayExitoso() public {
        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);

        assertEq(usdc.balanceOf(treasury), MONTO);
        assertEq(usdc.balanceOf(alice), 1000 * 10**6 - MONTO);
    }

    function test_PayEmitePaid() public {
        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);
    }

    function test_PayRevertSiMontoCero() public {
        vm.prank(alice);
        vm.expectRevert("El monto debe ser mayor a 0");
        gateway.pay(0, ACTION_ID);
    }

    function test_PayRevertSiSaldoInsuficiente() public {
        vm.prank(bob);
        vm.expectRevert("Saldo USDC insuficiente");
        gateway.pay(1, ACTION_ID);
    }

    function test_PayRevertSiAllowanceInsuficiente() public {
        vm.startPrank(alice);
        usdc.approve(address(gateway), 0);
        vm.expectRevert("Debe aprobar USDC al PaymentGateway primero");
        gateway.pay(MONTO, ACTION_ID);
        vm.stopPrank();
    }

    function test_PayRevertSiTransferFromFalla() public {
        PaymentGateway gatewayMal = new PaymentGateway(address(usdc), treasury);
        vm.prank(alice);
        vm.expectRevert("Debe aprobar USDC al PaymentGateway primero");
        gatewayMal.pay(1, ACTION_ID);
    }

    function test_PayMultipleVeces() public {
        vm.prank(alice);
        gateway.pay(50 * 10**6, ACTION_ID);
        vm.prank(alice);
        gateway.pay(30 * 10**6, ACTION_ID);

        assertEq(usdc.balanceOf(treasury), 80 * 10**6);
    }

    function test_PayActionIdDistinto() public {
        bytes32 accion1 = keccak256("proyecto-A");
        bytes32 accion2 = keccak256("proyecto-B");

        vm.startPrank(alice);
        gateway.pay(10 * 10**6, accion1);
        gateway.pay(20 * 10**6, accion2);
        vm.stopPrank();

        assertEq(usdc.balanceOf(treasury), 30 * 10**6);
    }

    function test_PayConMontoMinimo() public {
        vm.prank(alice);
        gateway.pay(1, ACTION_ID);
        assertEq(usdc.balanceOf(treasury), 1);
    }

    function test_PayConTodoElSaldo() public {
        vm.prank(alice);
        gateway.pay(1000 * 10**6, ACTION_ID);
        assertEq(usdc.balanceOf(alice), 0);
        assertEq(usdc.balanceOf(treasury), 1000 * 10**6);
    }

    function test_PayInvocanteDistintoPagador() public {
        usdc.mint(bob, 500 * 10**6);
        vm.prank(bob);
        usdc.approve(address(gateway), 500 * 10**6);

        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);
        assertEq(usdc.balanceOf(treasury), MONTO);

        vm.prank(bob);
        gateway.pay(200 * 10**6, ACTION_ID);
        assertEq(usdc.balanceOf(treasury), MONTO + 200 * 10**6);
    }

    // ============ SET TREASURY ============

    function test_SetTreasuryExitoso() public {
        vm.prank(treasury);
        gateway.setTreasury(bob);
        assertEq(gateway.treasury(), bob);
    }

    function test_SetTreasuryEmiteEvent() public {
        vm.startPrank(treasury);
        usdc.mint(address(gateway), MONTO);
        gateway.withdraw(MONTO);
        vm.stopPrank();
    }

    function test_SetTreasuryRevertSiCero() public {
        vm.prank(treasury);
        vm.expectRevert("Treasury no puede ser zero address");
        gateway.setTreasury(address(0));
    }

    function test_SetTreasuryRevertSiNoOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        gateway.setTreasury(bob);
    }

    function test_SetTreasuryRedirigePagos() public {
        vm.prank(treasury);
        gateway.setTreasury(bob);

        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);

        assertEq(usdc.balanceOf(bob), MONTO);
        assertEq(usdc.balanceOf(treasury), 0);
    }

    // ============ WITHDRAW ============

    function test_WithdrawExitoso() public {
        usdc.mint(address(gateway), MONTO);

        vm.prank(treasury);
        gateway.withdraw(MONTO);

        assertEq(usdc.balanceOf(treasury), MONTO);
        assertEq(usdc.balanceOf(address(gateway)), 0);
    }

    function test_WithdrawEmiteWithdrawn() public {
        usdc.mint(address(gateway), MONTO);

        vm.prank(treasury);
        gateway.withdraw(MONTO);
    }

    function test_WithdrawRevertSiNoOwner() public {
        vm.prank(alice);
        vm.expectRevert();
        gateway.withdraw(MONTO);
    }

    function test_WithdrawRevertSiSaldoInsuficiente() public {
        vm.prank(treasury);
        vm.expectRevert("Saldo insuficiente en el contrato");
        gateway.withdraw(1);
    }

    function test_WithdrawMontoCero() public {
        vm.prank(treasury);
        gateway.withdraw(0);

        assertEq(usdc.balanceOf(treasury), 0);
    }

    function test_WithdrawDespuesDePay() public {
        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);

        uint256 balanceGateway = usdc.balanceOf(address(gateway));
        assertEq(balanceGateway, 0);
    }

    function test_WithdrawSoloMontoDisponible() public {
        usdc.mint(address(gateway), MONTO);
        usdc.mint(address(gateway), 50 * 10**6);

        vm.prank(treasury);
        gateway.withdraw(MONTO);

        assertEq(usdc.balanceOf(treasury), MONTO);
        assertEq(usdc.balanceOf(address(gateway)), 50 * 10**6);
    }

    // ============ GET CONTRACT BALANCE ============

    function test_GetContractBalanceCero() public view {
        assertEq(gateway.getContractBalance(), 0);
    }

    function test_GetContractBalanceConFondos() public {
        usdc.mint(address(gateway), MONTO);
        assertEq(gateway.getContractBalance(), MONTO);
    }

    function test_GetContractBalanceDespuesDeWithdraw() public {
        usdc.mint(address(gateway), MONTO);
        vm.prank(treasury);
        gateway.withdraw(MONTO / 2);

        assertEq(gateway.getContractBalance(), MONTO - MONTO / 2);
    }

    // ============ FLUJOS COMPLEJOS ============

    function test_FlujoCompletoPagoRetiro() public {
        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);

        assertEq(usdc.balanceOf(treasury), MONTO);
        assertEq(usdc.balanceOf(alice), 1000 * 10**6 - MONTO);
    }

    function test_FlujoConCambioDeTreasury() public {
        vm.prank(treasury);
        gateway.setTreasury(bob);

        vm.prank(alice);
        gateway.pay(MONTO, ACTION_ID);

        assertEq(usdc.balanceOf(bob), MONTO);
        assertEq(usdc.balanceOf(treasury), 0);

        vm.prank(treasury);
        vm.expectRevert();
        gateway.withdraw(MONTO);
    }

    // ============ FUZZ ============

    function testFuzz_PayDiferentesMontos(uint24 raw) public {
        uint256 monto = uint256(raw);
        vm.assume(monto > 0 && monto <= usdc.balanceOf(alice));

        vm.prank(alice);
        gateway.pay(monto, ACTION_ID);

        assertTrue(usdc.balanceOf(treasury) >= monto);
    }

    function testFuzz_SetTreasuryDiferentesDirecciones(address nueva) public {
        vm.assume(nueva != address(0));

        vm.prank(treasury);
        gateway.setTreasury(nueva);

        assertEq(gateway.treasury(), nueva);
    }

    function testFuzz_PayConDiferentesActionId(bytes32 actionId) public {
        vm.assume(actionId != bytes32(0));

        vm.prank(alice);
        gateway.pay(MONTO, actionId);

        assertEq(usdc.balanceOf(treasury), MONTO);
    }

    receive() external payable {}
}
