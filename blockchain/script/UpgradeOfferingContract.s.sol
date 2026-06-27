// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/OfferingContract.sol";

/**
 * @notice Despliega el nuevo OfferingContract (v2 con lógica de Escrow)
 * y lo conecta con IdeaToken, IdeafyFactory y AuditOracle existentes.
 *
 * Comando para ejecutar:
 * forge script script/UpgradeOfferingContract.s.sol --rpc-url $SEPOLIA_RPC_URL --broadcast -vvvv
 */
contract UpgradeOfferingContract is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address backend = vm.addr(deployerPK);

        // Variables existentes en el .env (se asume que ya existen)
        address ideaToken = vm.envAddress("BLOCKCHAIN_IDEA_TOKEN");
        // Usamos la dirección hardcodeada para evitar la caché vieja de la terminal
        address factory = 0x462051fdda4eF66d00F2BbF4fE530570BF3Fd57A;
        address auditOracle = vm.envAddress("BLOCKCHAIN_AUDIT_ORACLE"); // Si no está en el .env, ponerla manual

        vm.startBroadcast(deployerPK);

        // 1. Deployar el nuevo OfferingContract
        OfferingContract newOffering = new OfferingContract(ideaToken, factory);

        // 2. Darle rol de ADMIN al backend
        newOffering.grantRole(newOffering.ADMIN_ROLE(), backend);

        // 3. Vincular el AuditOracle para que sepa qué Oráculo le dará permisos al Escrow
        newOffering.setAuditOracle(auditOracle);

        vm.stopBroadcast();

        console.log("=== ACTUALIZACION EXITOSA ===");
        console.log("NUEVO OfferingContract desplegado en: %s", address(newOffering));
        console.log("");
        console.log("IMPORTANTE: Reemplaza en tu backend/application.properties o .env:");
        console.log("systeam.blockchain.contract.offering=%s", address(newOffering));
    }
}
