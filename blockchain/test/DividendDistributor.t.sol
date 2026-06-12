// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/DividendDistributor.sol";
import "../contracts/IdeaToken.sol";
import "../contracts/IdeafyFactory.sol";
import "../contracts/SubToken.sol";

contract DividendDistributorTest is Test {

    event Distributed(uint256 indexed proyectoId, uint256 totalAmount, uint256 perToken);
    event Claimed(uint256 indexed proyectoId, address indexed user, uint256 amount);
    event TreasurySet(address indexed treasury);

    DividendDistributor public distributor;
    IdeaToken           public idea;
    IdeafyFactory       public factory;
    SubToken            public subTokenImpl;

    address public admin    = address(0x1);
    address public backend  = address(0x2);
    address public alice    = address(0x3);
    address public bob      = address(0x4);
    address public stranger = address(0x5);

    uint256 public constant PROYECTO_ID         = 1;
    uint256 public constant RUBRO_ID            = 2;
    uint256 public constant DIVIDEND_BPS        = 3000;
    uint256 public constant SUPPLY_INICIAL      = 1_000_000e18;
    uint256 public constant DISTRIBUTION_AMOUNT = 100_000e18;

    function setUp() public {
        // Admin deploys all contracts
        vm.startPrank(admin);

        idea = new IdeaToken();
        factory = new IdeafyFactory();
        subTokenImpl = new SubToken();
        factory.setSubTokenImplementation(address(subTokenImpl));
        factory.grantRole(factory.CREATOR_ROLE(), admin);

        // Launch project
        factory.launchProject(
            PROYECTO_ID, RUBRO_ID, DIVIDEND_BPS,
            admin, "Proyecto Test", "PRJ1", SUPPLY_INICIAL
        );

        // Deploy DividendDistributor
        distributor = new DividendDistributor(address(idea), address(factory));

        // Grant ADMIN_ROLE to backend (simulates deploy script step)
        distributor.grantRole(distributor.ADMIN_ROLE(), backend);

        // Link SubToken to DividendDistributor
        factory.setDividendDistributor(PROYECTO_ID, address(distributor));

        // Transfer IDEA from admin to backend for distribution tests
        idea.transfer(backend, DISTRIBUTION_AMOUNT * 10);

        vm.stopPrank();
    }

    function test_InitialState() public view {
        assertEq(address(distributor.idea()), address(idea));
        assertEq(address(distributor.factory()), address(factory));
        assertEq(distributor.treasury(), admin);
        assertTrue(distributor.hasRole(distributor.DEFAULT_ADMIN_ROLE(), admin));
        assertTrue(distributor.hasRole(distributor.ADMIN_ROLE(), backend));
        assertEq(distributor.DISTRIBUTION_FEE_BPS(), 50);
    }

    function test_OnlyAdminCanDistribute() public {
        vm.prank(stranger);
        vm.expectRevert();
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);
    }

    function test_BackendCanDistribute() public {
        uint256 backendBalanceBefore = idea.balanceOf(backend);

        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 fee = (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 net = DISTRIBUTION_AMOUNT - fee;

        assertEq(idea.balanceOf(backend), backendBalanceBefore - DISTRIBUTION_AMOUNT);
        assertEq(idea.balanceOf(admin), fee);
        assertEq(idea.balanceOf(address(distributor)), net);

        uint256 perToken = (net * 1e18) / SUPPLY_INICIAL;
        assertEq(distributor.dividendPerToken(PROYECTO_ID), perToken);
    }

    function test_EmitsDistributedEvent() public {
        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        uint256 net = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 perToken = (net * 1e18) / SUPPLY_INICIAL;

        vm.prank(backend);
        vm.expectEmit(true, true, true, true);
        emit Distributed(PROYECTO_ID, net, perToken);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);
    }

    function test_CannotDistributeZero() public {
        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        vm.expectRevert("DD: amount > 0");
        distributor.distribute(PROYECTO_ID, 0);
    }

    function test_CannotDistributeForNonExistentProject() public {
        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        vm.expectRevert("DD: project not found");
        distributor.distribute(999, DISTRIBUTION_AMOUNT);
    }

    function test_ClaimAfterDistribution() public {
        address subTokenAddr = factory.subTokenOfProject(PROYECTO_ID);
        uint256 aliceAmount = 100_000e18;

        vm.prank(admin);
        SubToken(subTokenAddr).transfer(alice, aliceAmount);

        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 net = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 perToken = (net * 1e18) / SUPPLY_INICIAL;
        uint256 expectedClaim = (perToken * aliceAmount) / 1e18;

        assertEq(distributor.getClaimable(PROYECTO_ID, alice), expectedClaim);

        vm.prank(alice);
        distributor.claim(PROYECTO_ID);

        assertEq(distributor.getClaimable(PROYECTO_ID, alice), 0);
    }

    function test_ClaimRevertsWhenNothingOwed() public {
        address subTokenAddr = factory.subTokenOfProject(PROYECTO_ID);

        vm.prank(admin);
        SubToken(subTokenAddr).transfer(alice, 100_000e18);

        vm.prank(alice);
        vm.expectRevert("DD: nothing to claim");
        distributor.claim(PROYECTO_ID);
    }

    function test_ClaimRevertsWithoutTokens() public {
        vm.prank(alice);
        vm.expectRevert("DD: no tokens");
        distributor.claim(PROYECTO_ID);
    }

    function test_ClaimEmitsEvent() public {
        address subTokenAddr = factory.subTokenOfProject(PROYECTO_ID);
        vm.prank(admin);
        SubToken(subTokenAddr).transfer(alice, 100_000e18);

        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 net = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 perToken = (net * 1e18) / SUPPLY_INICIAL;
        uint256 expectedClaim = (perToken * 100_000e18) / 1e18;

        vm.prank(alice);
        vm.expectEmit(true, true, true, true);
        emit Claimed(PROYECTO_ID, alice, expectedClaim);
        distributor.claim(PROYECTO_ID);
    }

    function test_GetClaimableReturnsZeroForUnknownProject() public view {
        assertEq(distributor.getClaimable(999, alice), 0);
    }

    function test_GetClaimableReturnsZeroForUserWithoutTokens() public view {
        assertEq(distributor.getClaimable(PROYECTO_ID, stranger), 0);
    }

    function test_DistributeAccumulatesPerToken() public {
        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT * 2);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 firstNet = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 firstPerToken = (firstNet * 1e18) / SUPPLY_INICIAL;
        assertEq(distributor.dividendPerToken(PROYECTO_ID), firstPerToken);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 secondNet = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 secondPerToken = firstPerToken + (secondNet * 1e18) / SUPPLY_INICIAL;
        assertEq(distributor.dividendPerToken(PROYECTO_ID), secondPerToken);
    }

    function test_TransferHookAccumulatesPendingDividends() public {
        address subTokenAddr = factory.subTokenOfProject(PROYECTO_ID);
        uint256 aliceAmount = 200_000e18;

        vm.prank(admin);
        SubToken(subTokenAddr).transfer(alice, aliceAmount);

        vm.prank(backend);
        idea.approve(address(distributor), DISTRIBUTION_AMOUNT);

        vm.prank(backend);
        distributor.distribute(PROYECTO_ID, DISTRIBUTION_AMOUNT);

        uint256 net = DISTRIBUTION_AMOUNT - (DISTRIBUTION_AMOUNT * 50) / 10000;
        uint256 perToken = (net * 1e18) / SUPPLY_INICIAL;

        uint256 transferAmount = 50_000e18;
        vm.prank(alice);
        SubToken(subTokenAddr).transfer(bob, transferAmount);

        uint256 expectedPending = ((perToken - 0) * aliceAmount) / 1e18;
        assertEq(distributor.pendingDividends(PROYECTO_ID, alice), expectedPending);
        assertEq(distributor.pendingDividends(PROYECTO_ID, bob), 0);
    }

    function test_OnTransferRevertsFromNonSubToken() public {
        vm.prank(stranger);
        vm.expectRevert("DD: not subToken");
        distributor.onTransfer(PROYECTO_ID, alice, bob, 1000);
    }

    function test_SetTreasury() public {
        address newTreasury = address(0x999);
        vm.prank(admin);
        vm.expectEmit(true, true, true, true);
        emit TreasurySet(newTreasury);
        distributor.setTreasury(newTreasury);

        assertEq(distributor.treasury(), newTreasury);
    }

    function test_OnlyAdminCanSetTreasury() public {
        vm.prank(stranger);
        vm.expectRevert();
        distributor.setTreasury(address(0x999));
    }

    function test_InvalidTreasuryReverts() public {
        vm.prank(admin);
        vm.expectRevert("DD: invalid treasury");
        distributor.setTreasury(address(0));
    }

    function test_NoDividendsByDefault() public {
        assertEq(distributor.dividendPerToken(PROYECTO_ID), 0);
        assertEq(distributor.getClaimable(PROYECTO_ID, alice), 0);
    }
}
