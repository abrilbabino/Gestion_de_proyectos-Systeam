// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/AuditOracle.sol";

/**
 * @notice Deploya el contrato AuditOracle en Sepolia.
 *
 * Uso:
 *   forge script script/DeployAuditOracle.s.sol \
 *     --rpc-url $SEPOLIA_RPC_URL \
 *     --broadcast \
 *     --verify \
 *     -vvvv
 *
 * Variables de entorno requeridas (en .env):
 *   BLOCKCHAIN_PRIVATE_KEY  → private key del deployer (hex, con 0x)
 *   BLOCKCHAIN_DEPLOYER     → address pública del deployer (para ADMIN_ROLE)
 */
contract DeployAuditOracle is Script {
    function run() external returns (AuditOracle oracle) {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address deployer   = vm.envAddress("BLOCKCHAIN_DEPLOYER");

        vm.startBroadcast(deployerPK);
        oracle = new AuditOracle(deployer);
        vm.stopBroadcast();

        console.log("AuditOracle deployed at: %s", address(oracle));
        console.log("Admin (DEFAULT_ADMIN_ROLE + ADMIN_ROLE): %s", deployer);
        console.log("Acordate de otorgar AUDITOR_ROLE a la wallet del backend con GrantAuditorRole.s.sol");
    }
}
