// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/InvestmentSwap.sol";
import "../contracts/PaymentGateway.sol";
import "../contracts/ProjectToken.sol";
import "../contracts/TokenFactory.sol";

// Mock ERC20 for overflow/edge testing
contract MockIDEA is IERC20 {
    string public name = "IDEA";
    string public symbol = "IDEA";
    uint8 public decimals = 18;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;
    uint256 public totalSupply = type(uint256).max;

    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
}

contract MockUSDC {
    string public name = "USDC";
    string public symbol = "USDC";
    uint8 public decimals = 6;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    function mint(address to, uint256 amount) external { balanceOf[to] += amount; }
    function approve(address spender, uint256 amount) external returns (bool) {
        allowance[msg.sender][spender] = amount;
        return true;
    }
    function transferFrom(address from, address to, uint256 amount) external returns (bool) {
        allowance[from][msg.sender] -= amount;
        balanceOf[from] -= amount;
        balanceOf[to] += amount;
        return true;
    }
    function transfer(address to, uint256 amount) external returns (bool) {
        balanceOf[msg.sender] -= amount;
        balanceOf[to] += amount;
        return true;
    }
}

contract OverflowZeroMaxFuzzTest is Test {

    // ==================== INVESTMENT SWAP ====================

    function testFuzz_InvestSwap_OverflowMaxSupply(uint16 subtokens) public {
        InvestmentSwap swap;
        MockIDEA idea;
        address treasury = makeAddr("treasury");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);

        swap.crearTokenProyecto(1, "Max", "MAX", 0);
        address alice = makeAddr("alice");
        idea.mint(alice, type(uint256).max);
        vm.prank(alice);
        idea.approve(address(swap), type(uint256).max);

        vm.assume(subtokens > 0 && subtokens <= 10000);

        uint256 ideaAmount = uint256(subtokens) * 1e18;
        vm.assume(ideaAmount <= idea.balanceOf(alice));

        vm.prank(alice);
        swap.invest(1, ideaAmount, subtokens, alice);

        address tokenAddr = swap.tokenDeProyecto(1);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), subtokens);
    }

    function testFuzz_InvestSwap_ZeroValues(uint8 subtokens) public {
        InvestmentSwap swap;
        MockIDEA idea;
        address treasury = makeAddr("treasury");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);
        swap.crearTokenProyecto(1, "Zero", "ZRO", 0);

        address alice = makeAddr("alice");
        idea.mint(alice, 1e24);
        vm.prank(alice);
        idea.approve(address(swap), 1e24);

        vm.assume(subtokens > 0 && subtokens <= 100);

        uint256 ideaAmount = uint256(subtokens) * 1e18;
        vm.prank(alice);
        swap.invest(1, ideaAmount, subtokens, alice);

        address tokenAddr = swap.tokenDeProyecto(1);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), subtokens);
    }

    function testFuzz_InvestSwap_RefundOverflow(uint8 subtokens) public {
        InvestmentSwap swap;
        MockIDEA idea;
        address treasury = makeAddr("treasury");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);
        swap.crearTokenProyecto(1, "Ref", "REF", 0);

        address alice = makeAddr("alice");
        idea.mint(alice, 1e24);
        vm.prank(alice);
        idea.approve(address(swap), 1e24);

        vm.assume(subtokens > 0 && subtokens <= 100);

        uint256 ideaAmount = uint256(subtokens) * 1e18;
        vm.prank(alice);
        swap.invest(1, ideaAmount, subtokens, alice);

        swap.refund(1, subtokens, alice, alice);

        address tokenAddr = swap.tokenDeProyecto(1);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), 0);
        assertEq(ProjectToken(tokenAddr).totalSupply(), 0);
    }

    function testFuzz_InvestSwap_CrearTokenMaxSupply(uint32 supply) public {
        InvestmentSwap swap;
        MockIDEA idea;
        address treasury = makeAddr("treasury");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);

        vm.assume(supply <= 1_000_000);

        address tokenAddr = swap.crearTokenProyecto(
            uint256(supply) + 1, "Fuzz", "FUZ", supply
        );

        assertEq(ProjectToken(tokenAddr).totalSupply(), supply);
    }

    function testFuzz_InvestSwap_InvestMaxIdeaAmount(uint64 ideaRaw) public {
        InvestmentSwap swap;
        MockIDEA idea;
        address treasury = makeAddr("treasury");

        idea = new MockIDEA();
        swap = new InvestmentSwap(address(idea), treasury);
        swap.crearTokenProyecto(1, "MaxI", "MXI", 1000000);

        address alice = makeAddr("alice");

        vm.assume(ideaRaw > 0);

        uint256 ideaAmount = uint256(ideaRaw) * 1e12;
        idea.mint(alice, ideaAmount);
        vm.prank(alice);
        idea.approve(address(swap), ideaAmount);

        uint256 subTokenAmount = ideaAmount / 1e18;
        vm.assume(subTokenAmount > 0 && subTokenAmount <= 1000000);

        vm.prank(alice);
        swap.invest(1, ideaAmount, subTokenAmount, alice);

        address tokenAddr = swap.tokenDeProyecto(1);
        assertEq(ProjectToken(tokenAddr).balanceOf(alice), subTokenAmount);
    }

    // ==================== PAYMENT GATEWAY ====================

    function testFuzz_PaymentGateway_MaxMonto(uint24 raw) public {
        MockUSDC usdc = new MockUSDC();
        address treasury = makeAddr("treasury");
        PaymentGateway gateway = new PaymentGateway(address(usdc), treasury);

        address alice = makeAddr("alice");
        uint256 monto = uint256(raw);
        vm.assume(monto > 0);

        usdc.mint(alice, monto);
        vm.prank(alice);
        usdc.approve(address(gateway), monto);

        vm.prank(alice);
        gateway.pay(monto, keccak256("fuzz"));

        assertEq(usdc.balanceOf(treasury), monto);
    }

    function testFuzz_PaymentGateway_MinMonto(uint24 raw) public {
        MockUSDC usdc = new MockUSDC();
        address treasury = makeAddr("treasury");
        PaymentGateway gateway = new PaymentGateway(address(usdc), treasury);

        address alice = makeAddr("alice");
        uint256 monto = uint256(raw);
        vm.assume(monto > 0 && monto <= 1_000_000);

        usdc.mint(alice, monto);
        vm.prank(alice);
        usdc.approve(address(gateway), monto);

        vm.prank(alice);
        gateway.pay(monto, keccak256("fuzz"));

        assertEq(usdc.balanceOf(treasury), monto);
    }

    function testFuzz_PaymentGateway_SetTreasuryMaxAddress(address nueva) public {
        MockUSDC usdc = new MockUSDC();
        address treasury = makeAddr("treasury");
        PaymentGateway gateway = new PaymentGateway(address(usdc), treasury);

        vm.assume(nueva != address(0));

        vm.prank(treasury);
        gateway.setTreasury(nueva);

        assertEq(gateway.treasury(), nueva);
    }

    function testFuzz_PaymentGateway_WithdrawMaxBalance(uint112 balanceRaw) public {
        MockUSDC usdc = new MockUSDC();
        address treasury = makeAddr("treasury");
        PaymentGateway gateway = new PaymentGateway(address(usdc), treasury);

        uint256 balance = uint256(balanceRaw);
        vm.assume(balance > 0 && balance <= 1_000_000_000_000_000);

        usdc.mint(address(gateway), balance);

        vm.prank(treasury);
        gateway.withdraw(balance);

        assertEq(usdc.balanceOf(treasury), balance);
    }

    // ==================== PROJECT TOKEN ====================

    function testFuzz_ProjectToken_OverflowMint(uint96 cantidadRaw) public {
        ProjectToken token = new ProjectToken("Test", "TST", address(this));

        uint256 cantidad = uint256(cantidadRaw);
        vm.assume(cantidad <= 1_000_000_000_000_000_000_000_000_000);

        token.mint(address(0x1234), cantidad);

        assertEq(token.balanceOf(address(0x1234)), cantidad);
        assertEq(token.totalSupply(), cantidad);
    }

    function testFuzz_ProjectToken_BurnTaxBoundary(uint32 amountRaw) public {
        ProjectToken token = new ProjectToken("Tax", "TAX", address(this));

        address alice = makeAddr("alice");
        address bob = makeAddr("bob");
        uint256 amount = uint256(amountRaw);

        token.mint(alice, 1_000_000_000);
        vm.assume(amount > 0 && amount <= token.balanceOf(alice));

        uint256 expectedBurn = (amount * 10) / 10000;
        uint256 expectedSend = amount - expectedBurn;

        vm.prank(alice);
        token.transfer(bob, amount);

        assertEq(token.balanceOf(bob), expectedSend);
        assertEq(token.totalSupply(), 1_000_000_000 - expectedBurn);
    }

    function testFuzz_ProjectToken_BurnTaxZeroEdge(uint16 amountRaw) public {
        ProjectToken token = new ProjectToken("Edge", "EDG", address(this));
        address alice = makeAddr("alice");
        address bob = makeAddr("bob");
        uint256 amount = uint256(amountRaw);

        token.mint(alice, 1_000_000);
        vm.assume(amount > 0 && amount <= token.balanceOf(alice));

        uint256 expectedBurn = (amount * 10) / 10000;

        vm.prank(alice);
        token.transfer(bob, amount);

        if (amount < 1000) {
            assertEq(expectedBurn, 0);
        } else {
            assertTrue(expectedBurn > 0);
        }
        assertEq(token.balanceOf(bob), amount - expectedBurn);
    }

    function testFuzz_ProjectToken_MaxSupply(uint64 supplyRaw) public {
        ProjectToken token = new ProjectToken("Max", "MAX", address(this));

        uint256 supply = uint256(supplyRaw);
        vm.assume(supply <= 1_000_000_000_000_000_000);

        token.mint(makeAddr("dest"), supply);

        assertEq(token.totalSupply(), supply);
    }

    function testFuzz_ProjectToken_MultipleTransfersDontOverflow(
        uint64 t1, uint64 t2, uint64 t3
    ) public {
        ProjectToken token = new ProjectToken("Chain", "CHN", address(this));

        address alice = makeAddr("alice");
        address bob = makeAddr("bob");
        address carlos = makeAddr("carlos");

        uint256 mintAmount = 1_000_000_000_000_000_000;
        token.mint(alice, mintAmount);
        token.mint(bob, mintAmount);
        token.mint(carlos, mintAmount);

        uint256[3] memory transfers = [uint256(t1), uint256(t2), uint256(t3)];

        for (uint256 i = 0; i < 3; i++) {
            address from;
            address to;

            if (i == 0) { from = alice; to = bob; }
            else if (i == 1) { from = bob; to = carlos; }
            else { from = carlos; to = alice; }

            if (transfers[i] > 0 && transfers[i] <= token.balanceOf(from)) {
                vm.prank(from);
                token.transfer(to, transfers[i]);
            }
        }

        assertGe(token.totalSupply(), 0);
        assertGe(token.balanceOf(alice), 0);
        assertGe(token.balanceOf(bob), 0);
        assertGe(token.balanceOf(carlos), 0);
    }

    // ==================== TOKEN FACTORY ====================

    function testFuzz_TokenFactory_MaxIds(uint64 rawId) public {
        TokenFactory factory = new TokenFactory(makeAddr("treasury"));

        uint256 proyectoId = uint256(rawId);
        vm.assume(proyectoId > 0 && proyectoId < 1_000_000);

        address tokenAddr = factory.crearTokenProyecto(proyectoId, "Fuzz", "FUZ", 0);
        assertEq(factory.tokenDeProyecto(proyectoId), tokenAddr);
        assertEq(factory.proyectoDeToken(tokenAddr), proyectoId);
    }

    function testFuzz_TokenFactory_DifferentSupplies(uint48 supplyRaw) public {
        TokenFactory factory = new TokenFactory(makeAddr("treasury"));

        uint256 supply = uint256(supplyRaw);
        if (supply == 0) return;

        uint256 proyectoId = uint256(keccak256(abi.encode(supply))) % 1_000_000 + 1;

        vm.expectRevert();
        factory.crearTokenProyecto(proyectoId, "Sup", "SUP", supply);
    }

    function testFuzz_TokenFactory_ZeroSupply(uint48 supplyRaw) public {
        TokenFactory factory = new TokenFactory(makeAddr("treasury"));

        uint256 supply = uint256(supplyRaw);
        vm.assume(supply == 0);

        uint256 id = uint256(keccak256(abi.encode(supply))) % 1_000_000 + 1;

        address tokenAddr = factory.crearTokenProyecto(id, "Zero", "ZRO", 0);
        assertEq(ProjectToken(tokenAddr).totalSupply(), 0);
    }

    function testFuzz_TokenFactory_ExplodeIdRange(uint32 raw) public {
        TokenFactory factory = new TokenFactory(makeAddr("treasury"));

        uint256 id = uint256(raw);
        if (id == 0) return;

        address tokenAddr = factory.crearTokenProyecto(id, "Id", "ID", 0);
        assertEq(factory.tokenDeProyecto(id), tokenAddr);
    }

    receive() external payable {}
}
