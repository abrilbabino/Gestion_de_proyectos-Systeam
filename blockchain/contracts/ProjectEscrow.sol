// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/utils/SafeERC20.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "./AuditOracle.sol";

/**
 * @title ProjectEscrow
 * @notice Retiene los tokens IDEA recaudados de un proyecto hasta que un auditor
 *         autorizado por el AuditOracle libere los fondos por hitos.
 */
contract ProjectEscrow is ReentrancyGuard {
    using SafeERC20 for IERC20;

    IERC20 public immutable ideaToken;
    AuditOracle public immutable auditOracle;
    address public immutable creator;
    uint256 public immutable proyectoId;

    event FundsReleased(address indexed to, uint256 amount);

    constructor(
        address _ideaToken,
        address _auditOracle,
        address _creator,
        uint256 _proyectoId
    ) {
        require(_ideaToken != address(0), "Escrow: invalid token");
        require(_auditOracle != address(0), "Escrow: invalid oracle");
        require(_creator != address(0), "Escrow: invalid creator");

        ideaToken = IERC20(_ideaToken);
        auditOracle = AuditOracle(_auditOracle);
        creator = _creator;
        proyectoId = _proyectoId;
    }

    /**
     * @dev Restringe el acceso solo a direcciones que tengan el rol de AUDITOR_ROLE
     *      en el contrato AuditOracle.
     */
    modifier onlyAuditor() {
        require(auditOracle.hasRole(auditOracle.AUDITOR_ROLE(), msg.sender), "Escrow: not auditor");
        _;
    }

    /**
     * @notice Libera una cantidad específica de tokens IDEA al creador del proyecto.
     * @param amount La cantidad de tokens IDEA (en wei) a liberar.
     */
    function releaseFunds(uint256 amount) external onlyAuditor nonReentrant {
        require(amount > 0, "Escrow: amount > 0");
        uint256 balance = ideaToken.balanceOf(address(this));
        require(balance >= amount, "Escrow: insufficient funds");

        ideaToken.safeTransfer(creator, amount);
        emit FundsReleased(creator, amount);
    }
}
