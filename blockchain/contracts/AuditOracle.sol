// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

/**
 * @title AuditOracle
 * @notice Registra on-chain los hallazgos de auditoría de proyectos Ideafy.
 *         Solo direcciones con rol AUDITOR_ROLE pueden reportar.
 *         El historial es inmutable: una vez registrado, no se puede modificar ni eliminar.
 *
 * @dev El backend (AuditOracleClient.java) es el único caller autorizado.
 *      El deployer recibe DEFAULT_ADMIN_ROLE y debe otorgar AUDITOR_ROLE
 *      a la wallet del backend via GrantAuditorRole.s.sol.
 */
contract AuditOracle is AccessControl, ReentrancyGuard {

    bytes32 public constant AUDITOR_ROLE = keccak256("AUDITOR_ROLE");
    bytes32 public constant ADMIN_ROLE   = keccak256("ADMIN_ROLE");

    /// @dev Resultado de la auditoría (espeja ResultadoAuditoria.java)
    uint8 public constant RESULTADO_APROBADO         = 0;
    uint8 public constant RESULTADO_RECHAZADO        = 1;
    uint8 public constant RESULTADO_NECESITA_CAMBIOS = 2;

    struct AuditRecord {
        uint256 proyectoId;
        address auditor;
        uint8   resultado;      // 0=APROBADO, 1=RECHAZADO, 2=NECESITA_CAMBIOS
        string  observaciones;
        bytes32 txHash;         // hash keccak256 generado por el backend (fingerprint único)
        uint256 timestamp;
    }

    /// @notice Historial de auditorías por proyecto (append-only)
    mapping(uint256 => AuditRecord[]) private _findings;

    /// @notice Previene que el mismo txHash sea reportado dos veces
    mapping(bytes32 => bool) public reportedHashes;

    // ─── Eventos ───────────────────────────────────────────────────────────────

    event AuditFindingSubmitted(
        uint256 indexed proyectoId,
        address indexed auditor,
        uint8           resultado,
        string          observaciones,
        bytes32 indexed txHash,
        uint256         timestamp
    );

    // ─── Errores custom ────────────────────────────────────────────────────────

    error InvalidResultado(uint8 resultado);
    error DuplicateTxHash(bytes32 txHash);
    error EmptyTxHash();

    // ─── Constructor ───────────────────────────────────────────────────────────

    constructor(address admin) {
        require(admin != address(0), "AuditOracle: admin is zero address");
        _grantRole(DEFAULT_ADMIN_ROLE, admin);
        _grantRole(ADMIN_ROLE, admin);
    }

    // ─── Funciones de escritura ─────────────────────────────────────────────────

    /**
     * @notice Registra un hallazgo de auditoría on-chain.
     * @param proyectoId    ID del proyecto auditado (coincide con la BD).
     * @param resultado     0=APROBADO, 1=RECHAZADO, 2=NECESITA_CAMBIOS.
     * @param observaciones Texto libre con observaciones del auditor.
     * @param txHash        Hash keccak256 generado por el backend como fingerprint único.
     */
    function submitAuditFinding(
        uint256 proyectoId,
        uint8   resultado,
        string  calldata observaciones,
        bytes32 txHash
    )
        external
        nonReentrant
        onlyRole(AUDITOR_ROLE)
    {
        if (resultado > 2) revert InvalidResultado(resultado);
        if (txHash == bytes32(0)) revert EmptyTxHash();
        if (reportedHashes[txHash]) revert DuplicateTxHash(txHash);

        reportedHashes[txHash] = true;

        _findings[proyectoId].push(AuditRecord({
            proyectoId:    proyectoId,
            auditor:       msg.sender,
            resultado:     resultado,
            observaciones: observaciones,
            txHash:        txHash,
            timestamp:     block.timestamp
        }));

        emit AuditFindingSubmitted(
            proyectoId,
            msg.sender,
            resultado,
            observaciones,
            txHash,
            block.timestamp
        );
    }

    // ─── Funciones de lectura ───────────────────────────────────────────────────

    /**
     * @notice Devuelve todos los hallazgos registrados para un proyecto.
     * @param proyectoId ID del proyecto.
     */
    function getFindings(uint256 proyectoId) external view returns (AuditRecord[] memory) {
        return _findings[proyectoId];
    }

    /**
     * @notice Cantidad de hallazgos registrados para un proyecto.
     */
    function findingCount(uint256 proyectoId) external view returns (uint256) {
        return _findings[proyectoId].length;
    }
}
