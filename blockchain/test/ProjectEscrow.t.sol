// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/ProjectEscrow.sol";
import "../contracts/AuditOracle.sol";
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract MockIdeaToken is ERC20 {
    constructor() ERC20("Mock IDEA", "IDEA") {}
    function mint(address to, uint256 amount) external {
        _mint(to, amount);
    }
}

contract ProjectEscrowTest is Test {
    ProjectEscrow public escrow;
    AuditOracle public oracle;
    MockIdeaToken public idea;

    address public admin = address(1);
    address public auditor = address(2);
    address public creator = address(3);
    address public randomUser = address(4);

    uint256 public constant PROYECTO_ID = 101;

    function setUp() public {
        // Deploy dependencies
        vm.startPrank(admin);
        idea = new MockIdeaToken();
        oracle = new AuditOracle();
        
        // Grant auditor role
        oracle.grantRole(oracle.AUDITOR_ROLE(), auditor);
        vm.stopPrank();

        // Deploy escrow
        escrow = new ProjectEscrow(address(idea), address(oracle), creator, PROYECTO_ID);

        // Fund escrow with 1000 IDEA
        idea.mint(address(escrow), 1000 * 1e18);
    }

    function testFail_ReleaseFundsNotAuditor() public {
        vm.prank(randomUser);
        escrow.releaseFunds(500 * 1e18); // Should revert
    }

    function test_ReleaseFundsSuccess() public {
        uint256 initialCreatorBalance = idea.balanceOf(creator);
        
        vm.prank(auditor);
        escrow.releaseFunds(500 * 1e18);

        uint256 finalCreatorBalance = idea.balanceOf(creator);
        uint256 escrowBalance = idea.balanceOf(address(escrow));

        assertEq(finalCreatorBalance - initialCreatorBalance, 500 * 1e18);
        assertEq(escrowBalance, 500 * 1e18);
    }

    function testFail_ReleaseMoreThanBalance() public {
        vm.prank(auditor);
        escrow.releaseFunds(1500 * 1e18); // Should revert because escrow only has 1000
    }
}
