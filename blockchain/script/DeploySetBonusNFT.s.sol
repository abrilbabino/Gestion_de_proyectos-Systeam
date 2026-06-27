// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/SetBonusNFT.sol";
import "../contracts/OfferingContract.sol";

contract DeploySetBonusNFT is Script {
    function run() external {
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address offeringContractAddress = 0xeCF71C7cBF98E6C1A9e31a557955cb29be77bbD4;

        vm.startBroadcast(deployerPK);

        // 1. Deploy the NFT contract
        SetBonusNFT nft = new SetBonusNFT();

        // 2. Transfer ownership to the OfferingContract so it can mint
        nft.transferOwnership(offeringContractAddress);
        
        // 3. Register the NFT inside the OfferingContract
        // (Since the deployer has ADMIN_ROLE, we can call it directly)
        OfferingContract offering = OfferingContract(offeringContractAddress);
        offering.setBonusNFTContract(address(nft));

        vm.stopBroadcast();
    }
}
