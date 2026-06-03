// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/OracleBilling.sol";
import "../contracts/IdeafyFactory.sol";
import "../contracts/SubToken.sol";
import "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";

contract OracleBillingTest is Test {

    event BillingAudited(
        uint256 indexed proyectoId,
        uint256         billingAmount,
        address indexed oracleAddress,
        bytes32 indexed txHash,
        uint256         timestamp
    );


    OracleBilling public oracle;
    IdeafyFactory public factory;
    SubToken      public subTokenImpl;

    address public admin  = address(0x1);
    address public oracleNode = address(0x2);
    address public issuer = address(0x3);
    address public stranger = address(0x4);

    uint256 public constant PROYECTO_ID    = 1;
    uint256 public constant RUBRO_ID       = 2;
    uint256 public constant DIVIDEND_BPS   = 3000;
    uint256 public constant SUPPLY_INICIAL = 1_000_000e18;
    uint256 public constant BILLING_AMOUNT = 50_000e18;
    bytes32 public constant TX_HASH        = keccak256("tx_oracle_001");

    function setUp() public {
        vm.startPrank(admin);

        factory = new IdeafyFactory();
        subTokenImpl = new SubToken();
        factory.setSubTokenImplementation(address(subTokenImpl));
        factory.grantRole(factory.CREATOR_ROLE(), admin);

        factory.launchProject(
            PROYECTO_ID, RUBRO_ID, DIVIDEND_BPS,
            admin, "Proyecto Test", "PRJ1", SUPPLY_INICIAL
        );

        oracle = new OracleBilling(address(factory));
        oracle.grantRole(oracle.ORACLE_ROLE(), oracleNode);
        oracle.grantRole(oracle.ISSUER_ROLE(), issuer);

        vm.stopPrank();
    }

    function test_OracleCanReportBilling() public {
        vm.prank(oracleNode);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);

        OracleBilling.BillingRecord memory record = oracle.getLatestBilling(PROYECTO_ID);
        assertEq(record.billingAmount, BILLING_AMOUNT);
        assertEq(record.oracleAddress, oracleNode);
        assertEq(record.txHash, TX_HASH);
        assertEq(oracle.totalBilling(PROYECTO_ID), BILLING_AMOUNT);
    }

    function test_IssuerCanReportBilling() public {
        vm.prank(issuer);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);

        assertEq(oracle.totalBilling(PROYECTO_ID), BILLING_AMOUNT);
    }

    function test_StrangerCannotReportBilling() public {
        vm.prank(stranger);
        vm.expectRevert("OracleBilling: not authorized");
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);
    }

    function test_EmitsBillingAuditedEvent() public {
        vm.prank(oracleNode);
        vm.expectEmit(true, true, true, true);
        emit BillingAudited(PROYECTO_ID, BILLING_AMOUNT, oracleNode, TX_HASH, block.timestamp);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);
    }

    function test_DuplicateTxHashReverts() public {
        vm.prank(oracleNode);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);

        vm.prank(oracleNode);
        vm.expectRevert("OracleBilling: txHash already reported");
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);
    }

    function test_ProjectNotFoundReverts() public {
        vm.prank(oracleNode);
        vm.expectRevert("OracleBilling: project not found");
        oracle.updateProjectBilling(999, BILLING_AMOUNT, TX_HASH);
    }

    function test_ZeroBillingReverts() public {
        vm.prank(oracleNode);
        vm.expectRevert("OracleBilling: billing > 0");
        oracle.updateProjectBilling(PROYECTO_ID, 0, TX_HASH);
    }

    function test_InvalidTxHashReverts() public {
        vm.prank(oracleNode);
        vm.expectRevert("OracleBilling: invalid txHash");
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, bytes32(0));
    }

    function test_AccumulatesBillingAcrossReports() public {
        bytes32 txHash2 = keccak256("tx_oracle_002");

        vm.prank(oracleNode);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, TX_HASH);

        vm.prank(oracleNode);
        oracle.updateProjectBilling(PROYECTO_ID, BILLING_AMOUNT, txHash2);

        assertEq(oracle.totalBilling(PROYECTO_ID), BILLING_AMOUNT * 2);
    }

    function test_NoProjectNoDividendsWithoutOracleData() public view {
        OracleBilling.BillingRecord memory record = oracle.getLatestBilling(PROYECTO_ID);
        assertEq(record.billingAmount, 0);
        assertEq(record.oracleAddress, address(0));
        assertEq(oracle.totalBilling(PROYECTO_ID), 0);
    }
}
