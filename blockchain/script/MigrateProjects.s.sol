// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "forge-std/Script.sol";
import "../contracts/IdeafyFactory.sol";
import "../contracts/OfferingContract.sol";

contract MigrateProjects is Script {
    function run() external {
        uint256 deployerPK = uint256(vm.envBytes32("BLOCKCHAIN_PRIVATE_KEY"));
        address backend = vm.addr(deployerPK);

        address factoryAddr = vm.envAddress("BLOCKCHAIN_IDEAFY_FACTORY");
        address offeringAddr = vm.envAddress("BLOCKCHAIN_OFFERING_CONTRACT");

        IdeafyFactory factory = IdeafyFactory(factoryAddr);
        OfferingContract offering = OfferingContract(offeringAddr);

        vm.startBroadcast(deployerPK);

        // Helper: crear SubToken si no existe
        _ensureSubToken(factory, 15, backend, "Sistema de Riego Int", "p15", 10000);
        _ensureSubToken(factory, 13, backend, "Test desde curl", "p13", 10000);
        _ensureSubToken(factory, 12, backend, "Test desde curl", "p12", 10000);
        _ensureSubToken(factory, 10, backend, "Proyecto Test Postman", "p10", 500);
        _ensureSubToken(factory, 9, backend, "Test", "p9", 500);
        _ensureSubToken(factory, 8, backend, "Proyecto Test Postman", "p8", 500);
        _ensureSubToken(factory, 7, backend, "Proyecto Test Postman", "p7", 500);
        _ensureSubToken(factory, 5, backend, "Mi proyecto Test - N", "p5", 100000);
        _ensureSubToken(factory, 4, backend, "Red de Energia Solar", "p4", 100000);

        // Registrar offerings solo para FINANCIAMIENTO
        // Proyecto 15: montoRequerido=50000, valorNominalToken=5, plazo=2026-06-28 19:15 UTC
        _ensureOffering(offering, 15, backend, 50000e18, 50000e18, 5e18, 1782674100);
        // Proyecto 5: sin monto, defaults, plazo=2026-06-13 02:54 UTC
        _ensureOffering(offering, 5, backend, 10000e18, 10000e18, 5e18, 1781319250);
        // Proyecto 4: sin monto, defaults, plazo=2026-06-13 03:25 UTC
        _ensureOffering(offering, 4, backend, 200000e18, 200000e18, 5e18, 1781321101);

        vm.stopBroadcast();
    }

    function _ensureSubToken(IdeafyFactory factory, uint256 pid, address creator,
        string memory name, string memory symbol, uint256 supply) internal
    {
        try factory.subTokenOfProject(pid) returns (address tokenAddr) {
            if (tokenAddr == address(0)) {
                factory.launchProject(pid, 1, 4000, creator, name, symbol, supply);
                console.log("ID %s: SubToken creado", pid);
            } else {
                console.log("ID %s: SubToken OK en %s", pid, tokenAddr);
            }
        } catch {
            factory.launchProject(pid, 1, 4000, creator, name, symbol, supply);
            console.log("ID %s: SubToken creado (subTokenOfProject fallo)", pid);
        }
    }

    function _ensureOffering(OfferingContract offering, uint256 pid, address creator,
        uint256 softCap, uint256 hardCap, uint256 price, uint256 endTime) internal
    {
        (uint256 pId,,,,,,,,,) = offering.offerings(pid);
        if (pId == 0) {
            offering.registerOffering(pid, creator, softCap, hardCap, price, block.timestamp, endTime);
            console.log("ID %s: Offering registrada (end=%s)", pid, endTime);
        } else {
            console.log("ID %s: Offering ya registrada (pId=%s)", pid, pId);
        }
    }
}
