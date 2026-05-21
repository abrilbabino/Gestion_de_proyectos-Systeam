// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract ProjectToken is ERC20, Ownable {

    uint256 public constant TASA_QUEMA = 10;

    event TokensQuemados(address indexed desde, uint256 cantidad);
    event TokensMinteados(address indexed para, uint256 cantidad);

    constructor(
        string memory nombre,
        string memory simbolo,
        address dueno
    ) ERC20(nombre, simbolo) Ownable(dueno) {}

    function mint(address para, uint256 cantidad) external onlyOwner {
        _mint(para, cantidad);
        emit TokensMinteados(para, cantidad);
    }

    function burnFrom(address desde, uint256 cantidad) external onlyOwner {
        _burn(desde, cantidad);
        emit TokensQuemados(desde, cantidad);
    }

    function _update(address desde, address para, uint256 valor) internal override {
        if (desde == address(0) || para == address(0)) {
            super._update(desde, para, valor);
            return;
        }

        uint256 cantidadAQuemar = (valor * TASA_QUEMA) / 10000;

        if (cantidadAQuemar == 0) {
            super._update(desde, para, valor);
            return;
        }

        uint256 cantidadAEnviar = valor - cantidadAQuemar;

        _burn(desde, cantidadAQuemar);
        emit TokensQuemados(desde, cantidadAQuemar);

        super._update(desde, para, cantidadAEnviar);
    }
}
