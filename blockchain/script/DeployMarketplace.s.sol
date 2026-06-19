// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeaMarketplace.sol";

contract DeployMarketplace is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address ideaToken = vm.envAddress("BLOCKCHAIN_IDEA_TOKEN");

        console.log("=== Deployer: %s ===", vm.addr(deployerPK));
        console.log("Using IdeaToken at: %s", ideaToken);

        vm.startBroadcast(deployerPK);
        IdeaMarketplace marketplace = new IdeaMarketplace(ideaToken);
        vm.stopBroadcast();

        console.log("");
        console.log("=== NUEVO MARKETPLACE DEPLOYADO ===");
        console.log("MARKETPLACE_ADDRESS=%s", address(marketplace));
        console.log("");
    }
}
