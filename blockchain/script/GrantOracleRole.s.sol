// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/OracleBilling.sol";

contract GrantOracleRole is Script {
    function run() external {
        uint256 pk = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address oracleAddr = vm.envAddress("ORACLE_BILLING_ADDRESS");
        address backend = vm.addr(pk);

        vm.startBroadcast(pk);
        OracleBilling oracle = OracleBilling(oracleAddr);
        oracle.grantRole(oracle.ORACLE_ROLE(), backend);
        console.log("ORACLE_ROLE granted to %s", backend);
        vm.stopBroadcast();
    }
}
