// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeaToken.sol";
import "../contracts/IdeaSwap.sol";
import "../contracts/MockUSDC.sol";

contract DeployLiquidity is Script {
    function run() external {
        // ── Config ────────────────────────────────────────────
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address backend = vm.addr(deployerPK);
        address ideaTokenAddress = vm.envAddress("BLOCKCHAIN_IDEA_TOKEN");

        console.log("=== CIRUGIA DE LIQUIDEZ: BASE SEPOLIA ===");
        console.log("Deployer: %s", backend);
        console.log("IdeaToken (Existente): %s", ideaTokenAddress);
        console.log("");

        // Asegurarnos de que el IdeaToken existe
        require(ideaTokenAddress != address(0), "Falta BLOCKCHAIN_IDEA_TOKEN en tu .env");
        IdeaToken ideaToken = IdeaToken(ideaTokenAddress);

        vm.startBroadcast(deployerPK);

        // 1. Desplegar el nuevo Dolar Falso (MockUSDC)
        MockUSDC usdc = new MockUSDC();
        console.log("-> NUEVO MockUSDC desplegado en: %s", address(usdc));

        // 2. Desplegar el nuevo Mercado (IdeaSwap)
        IdeaSwap swap = new IdeaSwap(address(ideaToken), address(usdc));
        console.log("-> NUEVO IdeaSwap desplegado en: %s", address(swap));

        // 3. Inyectar Liquidez
        // El constructor de MockUSDC ya le dio 100M de USDC al deployer.
        // Asumimos que el deployer todavía tiene al menos 1M de $IDEA de cuando se creó el token.
        
        uint256 liquidityAmount = 1_000_000 * 10**18;
        
        // Autorizamos al Swap a gastar nuestros tokens
        ideaToken.approve(address(swap), liquidityAmount);
        usdc.approve(address(swap), liquidityAmount);
        
        // Metemos la plata en la casa de cambio
        swap.addLiquidity(liquidityAmount, liquidityAmount);
        console.log("-> Liquidez inyectada: 1M de $IDEA y 1M de USDC");

        // Nota: El backend (deployer) se queda con los otros 99M de USDC en el bolsillo
        // para poder usarlos desde la Tesorería cuando el Oráculo facture.

        vm.stopBroadcast();

        // ── Output .env block ─────────────────────────────────
        console.log("");
        console.log("=== EXITOSO: ACTUALIZA TU .ENV CON ESTO ===");
        console.log("BLOCKCHAIN_USDC=%s", address(usdc));
        console.log("BLOCKCHAIN_IDEA_SWAP=%s", address(swap));
        console.log("");
        console.log("Reemplaza SOLO estas 2 lineas en tu archivo .env y reinicia el servidor Java.");
        console.log("Tus proyectos anteriores siguen intactos y funcionando.");
    }
}
