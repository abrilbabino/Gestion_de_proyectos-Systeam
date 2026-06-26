// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/AuditOracle.sol";

/**
 * @notice Otorga AUDITOR_ROLE a la wallet del backend en un AuditOracle ya deployado.
 *
 * Uso:
 *   forge script script/GrantAuditorRole.s.sol \
 *     --rpc-url $SEPOLIA_RPC_URL \
 *     --broadcast \
 *     -vvvv
 *
 * Variables de entorno requeridas (en .env):
 *   BLOCKCHAIN_PRIVATE_KEY       → private key del admin
 *   BLOCKCHAIN_AUDIT_ORACLE      → address del contrato AuditOracle
 *   BLOCKCHAIN_BACKEND_WALLET    → address de la wallet del backend (la que firma las txs)
 */
contract GrantAuditorRole is Script {
    function run() external {
        uint256 adminPK       = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address oracleAddr    = vm.envAddress("BLOCKCHAIN_AUDIT_ORACLE");
        address backendWallet = vm.envAddress("BLOCKCHAIN_BACKEND_WALLET");

        AuditOracle oracle = AuditOracle(oracleAddr);

        vm.startBroadcast(adminPK);
        oracle.grantRole(oracle.AUDITOR_ROLE(), backendWallet);
        vm.stopBroadcast();

        console.log("AUDITOR_ROLE otorgado a: %s", backendWallet);
    }
}
