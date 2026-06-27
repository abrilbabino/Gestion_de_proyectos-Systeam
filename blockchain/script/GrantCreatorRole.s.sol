// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeafyFactory.sol";

contract GrantCreatorRole is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address backend = vm.addr(deployerPK);

        // Usamos la dirección hardcodeada porque la terminal estaba usando una variable vieja en caché
        address factoryAddr = 0x462051fdda4eF66d00F2BbF4fE530570BF3Fd57A;

        vm.startBroadcast(deployerPK);

        IdeafyFactory factory = IdeafyFactory(factoryAddr);
        
        // Le damos el rol al backend
        factory.grantRole(factory.CREATOR_ROLE(), backend);

        vm.stopBroadcast();

        console.log("=== ROL ASIGNADO EXITOSAMENTE ===");
        console.log("La wallet del backend (%s) ahora tiene CREATOR_ROLE", backend);
    }
}
