// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeaToken.sol";
import "../contracts/SubToken.sol";
import "../contracts/IdeafyFactory.sol";
import "../contracts/OfferingContract.sol";
import "../contracts/IdeaMarketplace.sol";
import "../contracts/IdeaGovernance.sol";
import "../contracts/IdeaSwap.sol";
import "../contracts/DividendDistributor.sol";

contract DeployAll is Script {
    function run() external {
        // ── Config ────────────────────────────────────────────
        address usdc = vm.envAddress("BLOCKCHAIN_USDC");
        uint256 deployerPK = vm.envUint("BLOCKCHAIN_PRIVATE_KEY");
        address backend = vm.addr(deployerPK);

        console.log("=== Deployer: %s ===", backend);
        console.log("USDC:       %s", usdc);
        console.log("");

        vm.startBroadcast(deployerPK);

        // 1. IdeaToken
        IdeaToken ideaToken = new IdeaToken();
        console.log("IdeaToken:  %s", address(ideaToken));

        // 2. SubToken (implementation — upgradeable via proxy)
        SubToken subTokenImpl = new SubToken();
        console.log("SubToken:   %s", address(subTokenImpl));

        // 3. IdeafyFactory
        IdeafyFactory factory = new IdeafyFactory();
        console.log("IdeafyFactory: %s", address(factory));

        // 4. Set SubToken implementation on Factory
        factory.setSubTokenImplementation(address(subTokenImpl));
        console.log("  -> SubToken implementation set on Factory");

        // 5. Grant CREATOR_ROLE to backend on Factory
        bytes32 creatorRole = factory.CREATOR_ROLE();
        factory.grantRole(creatorRole, backend);
        console.log("  -> CREATOR_ROLE granted to %s", backend);

        // 6. OfferingContract
        OfferingContract offering = new OfferingContract(address(ideaToken), address(factory));
        console.log("OfferingContract: %s", address(offering));

        // 7. Grant ADMIN_ROLE to backend on OfferingContract
        bytes32 adminRole = offering.ADMIN_ROLE();
        offering.grantRole(adminRole, backend);
        console.log("  -> ADMIN_ROLE granted to %s on Offering", backend);

        // 8. IdeaMarketplace
        IdeaMarketplace marketplace = new IdeaMarketplace(address(ideaToken));
        console.log("IdeaMarketplace: %s", address(marketplace));

        // 9. IdeaGovernance
        IdeaGovernance governance = new IdeaGovernance(address(ideaToken));
        console.log("IdeaGovernance: %s", address(governance));

        // 10. IdeaSwap
        IdeaSwap swap = new IdeaSwap(address(ideaToken), usdc);
        console.log("IdeaSwap:   %s", address(swap));

        // 11. DividendDistributor
        DividendDistributor distributor = new DividendDistributor(address(ideaToken), address(factory));
        console.log("DividendDistributor: %s", address(distributor));

        vm.stopBroadcast();

        // ── Output .env block ─────────────────────────────────
        console.log("");
        console.log("=== COPY-PASTA ESTO A TU .env ===");
        console.log("");
        console.log("BLOCKCHAIN_IDEA_TOKEN=%s", address(ideaToken));
        console.log("BLOCKCHAIN_IDEAFY_FACTORY=%s", address(factory));
        console.log("BLOCKCHAIN_OFFERING_CONTRACT=%s", address(offering));
        console.log("BLOCKCHAIN_DIVIDEND_DISTRIBUTOR=%s", address(distributor));
        console.log("BLOCKCHAIN_IDEA_SWAP=%s", address(swap));
        console.log("BLOCKCHAIN_IDEA_MARKETPLACE=%s", address(marketplace));
        console.log("BLOCKCHAIN_IDEA_GOVERNANCE=%s", address(governance));
        console.log("");

        // Validate
        console.log("=== VERIFICACION ===");
        console.log("Factory has SubToken impl: %s", factory.subTokenImplementation() == address(subTokenImpl) ? "OK" : "FAIL");
        console.log("Backend has CREATOR_ROLE:  %s", factory.hasRole(creatorRole, backend) ? "OK" : "FAIL");
        console.log("Backend has ADMIN_ROLE:    %s", offering.hasRole(adminRole, backend) ? "OK" : "FAIL");
    }
}
