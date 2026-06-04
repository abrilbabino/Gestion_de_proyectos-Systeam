// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/OracleBilling.sol";

contract DeployOracleBilling is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address factory = vm.envAddress("BLOCKCHAIN_IDEAFY_FACTORY");

        vm.startBroadcast(deployerPK);
        OracleBilling oracle = new OracleBilling(factory);
        console.log("OracleBilling: %s", address(oracle));
        vm.stopBroadcast();
    }
}
