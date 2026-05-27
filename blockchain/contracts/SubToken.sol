// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts-upgradeable/token/ERC20/ERC20Upgradeable.sol";
import "@openzeppelin/contracts-upgradeable/proxy/utils/UUPSUpgradeable.sol";

contract SubToken is ERC20Upgradeable, UUPSUpgradeable {

    uint256 public proyectoId;
    uint256 public rubroId;
    uint256 public dividendBps;
    address public creator;
    address public factory;

    /// @custom:oz-upgrades-unsafe-allow constructor
    constructor() {
        _disableInitializers();
    }

    function initialize(
        uint256 _proyectoId,
        uint256 _rubroId,
        uint256 _dividendBps,
        address _creator,
        address _factory,
        string memory nombre,
        string memory simbolo,
        uint256 supplyInicial
    ) external initializer {
        require(_factory != address(0), "SubToken: invalid factory");

        __ERC20_init(nombre, simbolo);
        __UUPSUpgradeable_init();

        proyectoId = _proyectoId;
        rubroId = _rubroId;
        dividendBps = _dividendBps;
        creator = _creator;
        factory = _factory;

        _mint(_factory, supplyInicial);
    }

    function burnFrom(address desde, uint256 cantidad) external {
        require(msg.sender == factory, "SubToken: only factory");
        _burn(desde, cantidad);
    }

    function _authorizeUpgrade(address newImplementation) internal override {
        require(msg.sender == factory, "SubToken: only factory can upgrade");
    }
}
