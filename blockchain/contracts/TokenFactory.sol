// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./ProjectToken.sol";

contract TokenFactory {

    event TokenCreado(
        uint256 indexed proyectoId,
        address indexed tokenAddress,
        string nombre,
        string simbolo
    );

    address public immutable treasury;
    address[] public tokensCreados;
    mapping(uint256 => address) public tokenDeProyecto;
    mapping(address => uint256) public proyectoDeToken;

    constructor(address _treasury) {
        require(_treasury != address(0), "TokenFactory: treasury cannot be zero");
        treasury = _treasury;
    }

    function crearTokenProyecto(
        uint256 proyectoId,
        string memory nombre,
        string memory simbolo,
        uint256 supplyInicial
    ) external returns (address) {
        require(
            tokenDeProyecto[proyectoId] == address(0),
            "TokenFactory: ya existe un token para este proyecto"
        );

        ProjectToken nuevoToken = new ProjectToken(nombre, simbolo, treasury);

        address tokenAddress = address(nuevoToken);

        if (supplyInicial > 0) {
            nuevoToken.mint(treasury, supplyInicial);
        }

        tokenDeProyecto[proyectoId] = tokenAddress;
        proyectoDeToken[tokenAddress] = proyectoId;
        tokensCreados.push(tokenAddress);

        emit TokenCreado(proyectoId, tokenAddress, nombre, simbolo);

        return tokenAddress;
    }

    function obtenerCantidadTokens() external view returns (uint256) {
        return tokensCreados.length;
    }

    function obtenerTokenPorIndice(uint256 indice) external view returns (address) {
        require(indice < tokensCreados.length, "TokenFactory: indice fuera de rango");
        return tokensCreados[indice];
    }
}
