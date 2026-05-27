// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";

contract SubToken is ERC20 {

    uint256 public immutable proyectoId;
    uint256 public immutable rubroId;
    uint256 public immutable dividendBps;
    address public immutable creator;
    address public immutable factory;

    constructor(
        uint256 _proyectoId,
        uint256 _rubroId,
        uint256 _dividendBps,
        address _creator,
        address _factory,
        string memory nombre,
        string memory simbolo,
        uint256 supplyInicial
    ) ERC20(nombre, simbolo) {
        proyectoId = _proyectoId;
        rubroId = _rubroId;
        dividendBps = _dividendBps;
        creator = _creator;
        factory = _factory;
        _mint(_factory, supplyInicial);
    }

    modifier onlyFactory() {
        require(msg.sender == factory, "SubToken: only factory");
        _;
    }

    function burnFrom(address desde, uint256 cantidad) external onlyFactory {
        _burn(desde, cantidad);
    }
}
