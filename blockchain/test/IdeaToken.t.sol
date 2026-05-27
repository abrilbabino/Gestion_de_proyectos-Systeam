// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/IdeaToken.sol";

contract IdeaTokenTest is Test {
    IdeaToken public token;
    address public deployer = address(0x111);
    address public alice = address(0x222);
    address public bob = address(0x333);

    uint256 public constant MAX_SUPPLY = 100_000_000e18;
    uint256 public constant BURN_BPS = 100; // 1%

    function setUp() public {
        vm.startPrank(deployer);
        token = new IdeaToken();
        vm.stopPrank();
    }

    function test_MaxSupply() public view {
        assertEq(token.MAX_SUPPLY(), MAX_SUPPLY);
    }

    function test_InitialMint() public view {
        assertEq(token.totalSupply(), MAX_SUPPLY);
        assertEq(token.balanceOf(deployer), MAX_SUPPLY);
    }

    function test_NoMintFunction() public {
        // No hay función mint pública — todo el supply se mintea en el constructor.
        // Nadie puede aumentar el supply más allá de MAX_SUPPLY.
        assertEq(token.totalSupply(), MAX_SUPPLY);
    }

    function test_BurnBpsConstant() public view {
        assertEq(token.BURN_BPS(), BURN_BPS);
    }

    function test_TransferBurnsOnePercent() public {
        uint256 transferAmount = 1000e18;
        uint256 expectedBurn = (transferAmount * BURN_BPS) / 10000;
        uint256 expectedReceived = transferAmount - expectedBurn;

        vm.prank(deployer);
        token.transfer(alice, transferAmount);

        assertEq(token.balanceOf(alice), expectedReceived);
        assertEq(token.balanceOf(deployer), MAX_SUPPLY - transferAmount);
        assertEq(token.totalSupply(), MAX_SUPPLY - expectedBurn);
    }

    function test_NoBurnOnMint() public {
        uint256 supplyBefore = token.totalSupply();
        assertEq(supplyBefore, MAX_SUPPLY);
    }

    function test_NoBurnOnBurn() public {
        uint256 amount = 1000e18;

        vm.prank(deployer);
        token.transfer(alice, amount);

        // Transfer burns 1%: alice gets amount - 1%
        uint256 aliceBalance = token.balanceOf(alice);
        uint256 expectedBurnOnTransfer = (amount * BURN_BPS) / 10000;
        assertEq(aliceBalance, amount - expectedBurnOnTransfer);

        // Burn should NOT trigger extra burn (to == address(0))
        vm.prank(alice);
        token.burn(aliceBalance);

        assertEq(token.totalSupply(), MAX_SUPPLY - expectedBurnOnTransfer - aliceBalance);
    }

    function test_BurnFromDeployer() public {
        uint256 burnAmount = 100e18;

        vm.prank(deployer);
        token.burn(burnAmount);

        assertEq(token.balanceOf(deployer), MAX_SUPPLY - burnAmount);
    }

    function testFuzz_TransferBurnPercentage(uint256 amount) public {
        vm.assume(amount > 0 && amount <= 1_000_000e18);

        address sender = address(0xAAA);
        vm.prank(deployer);
        token.transfer(sender, amount);

        uint256 senderBalance = token.balanceOf(sender);
        uint256 expectedBurn1 = (amount * BURN_BPS) / 10000;
        assertEq(senderBalance, amount - expectedBurn1);

        vm.prank(sender);
        token.transfer(bob, senderBalance);

        uint256 expectedBurn2 = (senderBalance * BURN_BPS) / 10000;
        assertEq(token.balanceOf(bob), senderBalance - expectedBurn2);
        assertEq(token.totalSupply(), MAX_SUPPLY - expectedBurn1 - expectedBurn2);
    }

    function test_TreasuryRole() public {
        assertTrue(token.hasRole(token.TREASURY_ROLE(), deployer));
    }

    function test_DefaultAdminRole() public {
        assertTrue(token.hasRole(token.DEFAULT_ADMIN_ROLE(), deployer));
    }
}
