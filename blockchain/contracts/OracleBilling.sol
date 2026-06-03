// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./IdeafyFactory.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract OracleBilling is AccessControl, ReentrancyGuard {

    bytes32 public constant ORACLE_ROLE  = keccak256("ORACLE_ROLE");
    bytes32 public constant ISSUER_ROLE  = keccak256("ISSUER_ROLE");
    bytes32 public constant ADMIN_ROLE   = keccak256("ADMIN_ROLE");

    IdeafyFactory public immutable factory;

    struct BillingRecord {
        uint256 billingAmount;
        uint256 timestamp;
        address oracleAddress;
        bytes32 txHash;
    }

    mapping(uint256 => mapping(bytes32 => bool)) public reportedTxHashes;
    mapping(uint256 => BillingRecord)             public latestBilling;
    mapping(uint256 => uint256)                   public totalBilling;

    event BillingAudited(
        uint256 indexed proyectoId,
        uint256         billingAmount,
        address indexed oracleAddress,
        bytes32 indexed txHash,
        uint256         timestamp
    );

    modifier onlyAuthorizedReporter() {
        require(
            hasRole(ORACLE_ROLE, msg.sender) || hasRole(ISSUER_ROLE, msg.sender),
            "OracleBilling: not authorized"
        );
        _;
    }

    constructor(address _factory) {
        require(_factory != address(0), "OracleBilling: invalid factory");
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function updateProjectBilling(
        uint256 proyectoId,
        uint256 billingAmount,
        bytes32 txHash
    )
        external
        nonReentrant
        onlyAuthorizedReporter
    {
        require(factory.subTokenOfProject(proyectoId) != address(0), "OracleBilling: project not found");
        require(billingAmount > 0,        "OracleBilling: billing > 0");
        require(txHash != bytes32(0),     "OracleBilling: invalid txHash");
        require(
            !reportedTxHashes[proyectoId][txHash],
            "OracleBilling: txHash already reported"
        );

        reportedTxHashes[proyectoId][txHash] = true;
        totalBilling[proyectoId] += billingAmount;
        latestBilling[proyectoId] = BillingRecord({
            billingAmount: billingAmount,
            timestamp:     block.timestamp,
            oracleAddress: msg.sender,
            txHash:        txHash
        });

        emit BillingAudited(proyectoId, billingAmount, msg.sender, txHash, block.timestamp);
    }

    function getLatestBilling(uint256 proyectoId) external view returns (BillingRecord memory) {
        return latestBilling[proyectoId];
    }
}
