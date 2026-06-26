// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Test.sol";
import "../contracts/AuditOracle.sol";

contract AuditOracleTest is Test {

    // ─── Eventos (espejo para expectEmit) ──────────────────────────────────────
    event AuditFindingSubmitted(
        uint256 indexed proyectoId,
        address indexed auditor,
        uint8           resultado,
        string          observaciones,
        bytes32 indexed txHash,
        uint256         timestamp
    );

    // ─── Actores ───────────────────────────────────────────────────────────────
    AuditOracle internal oracle;
    address internal admin         = makeAddr("admin");
    address internal backendWallet = makeAddr("backendWallet");
    address internal stranger      = makeAddr("stranger");

    // ─── Fixtures ──────────────────────────────────────────────────────────────
    uint256 internal constant PROYECTO_ID   = 42;
    bytes32 internal constant TX_HASH_1     = keccak256("payload_proyecto_42_aprobado");
    bytes32 internal constant TX_HASH_2     = keccak256("payload_proyecto_42_necesita");
    string  internal constant OBS_APROBADO  = "Documentacion completa, viabilidad alta.";
    string  internal constant OBS_RECHAZADO = "Falta documentacion legal, riesgo alto.";

    // ─── Setup ─────────────────────────────────────────────────────────────────
    function setUp() public {
        vm.startPrank(admin);
        oracle = new AuditOracle(admin);
        oracle.grantRole(oracle.AUDITOR_ROLE(), backendWallet);
        vm.stopPrank();
    }

    // ─── Happy paths ───────────────────────────────────────────────────────────

    function test_SubmitFinding_Aprobado() public {
        vm.prank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 0, OBS_APROBADO, TX_HASH_1);

        AuditOracle.AuditRecord[] memory records = oracle.getFindings(PROYECTO_ID);
        assertEq(records.length, 1);
        assertEq(records[0].proyectoId,    PROYECTO_ID);
        assertEq(records[0].auditor,       backendWallet);
        assertEq(records[0].resultado,     0);
        assertEq(records[0].observaciones, OBS_APROBADO);
        assertEq(records[0].txHash,        TX_HASH_1);
        assertGt(records[0].timestamp,     0);
    }

    function test_SubmitFinding_Rechazado() public {
        vm.prank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 1, OBS_RECHAZADO, TX_HASH_1);

        AuditOracle.AuditRecord[] memory records = oracle.getFindings(PROYECTO_ID);
        assertEq(records[0].resultado, 1);
    }

    function test_SubmitFinding_NecesitaCambios() public {
        vm.prank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 2, "Revisar seccion financiera", TX_HASH_1);

        AuditOracle.AuditRecord[] memory records = oracle.getFindings(PROYECTO_ID);
        assertEq(records[0].resultado, 2);
    }

    function test_EmitsFindingSubmittedEvent() public {
        vm.prank(backendWallet);
        vm.expectEmit(true, true, true, false); // proyectoId, auditor, txHash indexed; no chequeamos data
        emit AuditFindingSubmitted(PROYECTO_ID, backendWallet, 0, OBS_APROBADO, TX_HASH_1, block.timestamp);
        oracle.submitAuditFinding(PROYECTO_ID, 0, OBS_APROBADO, TX_HASH_1);
    }

    function test_AccumulatesMultipleFindingsPerProject() public {
        vm.startPrank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 2, "Primera revision", TX_HASH_1);
        oracle.submitAuditFinding(PROYECTO_ID, 0, "Segunda revision, ok", TX_HASH_2);
        vm.stopPrank();

        assertEq(oracle.findingCount(PROYECTO_ID), 2);
        AuditOracle.AuditRecord[] memory records = oracle.getFindings(PROYECTO_ID);
        assertEq(records[0].resultado, 2);
        assertEq(records[1].resultado, 0);
    }

    function test_FindingsAreIndependentPerProject() public {
        bytes32 hashProy99 = keccak256("payload_proyecto_99");
        vm.startPrank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 0, "ok", TX_HASH_1);
        oracle.submitAuditFinding(99, 1, "rechazado", hashProy99);
        vm.stopPrank();

        assertEq(oracle.findingCount(PROYECTO_ID), 1);
        assertEq(oracle.findingCount(99), 1);
    }

    function test_ReportedHashMarkedAsUsed() public {
        vm.prank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 0, "ok", TX_HASH_1);
        assertTrue(oracle.reportedHashes(TX_HASH_1));
    }

    // ─── Casos de error ────────────────────────────────────────────────────────

    function test_Revert_OnlyAuditorRole() public {
        vm.prank(stranger);
        vm.expectRevert(); // AccessControl revierte con su propio error
        oracle.submitAuditFinding(PROYECTO_ID, 0, OBS_APROBADO, TX_HASH_1);
    }

    function test_Revert_InvalidResultado() public {
        vm.prank(backendWallet);
        vm.expectRevert(abi.encodeWithSelector(AuditOracle.InvalidResultado.selector, 3));
        oracle.submitAuditFinding(PROYECTO_ID, 3, "invalido", TX_HASH_1);
    }

    function test_Revert_EmptyTxHash() public {
        vm.prank(backendWallet);
        vm.expectRevert(AuditOracle.EmptyTxHash.selector);
        oracle.submitAuditFinding(PROYECTO_ID, 0, OBS_APROBADO, bytes32(0));
    }

    function test_Revert_DuplicateTxHash() public {
        vm.startPrank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 0, OBS_APROBADO, TX_HASH_1);

        vm.expectRevert(abi.encodeWithSelector(AuditOracle.DuplicateTxHash.selector, TX_HASH_1));
        oracle.submitAuditFinding(PROYECTO_ID, 1, OBS_RECHAZADO, TX_HASH_1); // mismo hash
        vm.stopPrank();
    }

    function test_EmptyProjectReturnsEmptyArray() public view {
        AuditOracle.AuditRecord[] memory records = oracle.getFindings(999);
        assertEq(records.length, 0);
        assertEq(oracle.findingCount(999), 0);
    }

    // ─── Fuzz ──────────────────────────────────────────────────────────────────

    function testFuzz_AcceptsAllValidResultados(uint8 resultado) public {
        vm.assume(resultado <= 2);
        bytes32 h = keccak256(abi.encode("fuzz", resultado));
        vm.prank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, resultado, "fuzz", h);
        assertEq(oracle.findingCount(PROYECTO_ID), 1);
    }

    function testFuzz_RejectsInvalidResultados(uint8 resultado) public {
        vm.assume(resultado > 2);
        bytes32 h = keccak256(abi.encode("fuzz-invalid", resultado));
        vm.prank(backendWallet);
        vm.expectRevert(abi.encodeWithSelector(AuditOracle.InvalidResultado.selector, resultado));
        oracle.submitAuditFinding(PROYECTO_ID, resultado, "fuzz", h);
    }

    function testFuzz_NoDuplicateHashesAllowed(bytes32 h) public {
        vm.assume(h != bytes32(0));
        vm.startPrank(backendWallet);
        oracle.submitAuditFinding(PROYECTO_ID, 0, "first", h);
        vm.expectRevert(abi.encodeWithSelector(AuditOracle.DuplicateTxHash.selector, h));
        oracle.submitAuditFinding(PROYECTO_ID, 0, "second", h);
        vm.stopPrank();
    }
}
