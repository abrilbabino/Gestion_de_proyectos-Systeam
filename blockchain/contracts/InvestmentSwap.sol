// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./ProjectToken.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract InvestmentSwap is Ownable, ReentrancyGuard {

    event TokenDeProyectoCreado(
        uint256 indexed proyectoId,
        address indexed tokenAddress
    );

    event InvestmentMade(
        uint256 indexed proyectoId,
        address indexed investor,
        uint256 ideaAmount,
        uint256 subTokenAmount
    );

    event RefundMade(
        uint256 indexed proyectoId,
        address indexed investor,
        uint256 ideaAmount,
        uint256 subTokenAmount
    );

    IERC20 public immutable idea;
    address public immutable treasury;
    mapping(uint256 => address) public tokenDeProyecto;
    mapping(address => uint256) public proyectoDeToken;
    address[] public tokensCreados;

    constructor(address _idea, address _treasury) Ownable(msg.sender) {
        require(_idea != address(0), "IDEA address required");
        require(_treasury != address(0), "Treasury address required");
        idea = IERC20(_idea);
        treasury = _treasury;
    }

    function crearTokenProyecto(
        uint256 proyectoId,
        string calldata nombre,
        string calldata simbolo,
        uint256 supplyInicial
    ) external onlyOwner returns (address) {
        require(tokenDeProyecto[proyectoId] == address(0),
            "InvestmentSwap: token already exists for this project");
        ProjectToken nuevoToken = new ProjectToken(nombre, simbolo, address(this));
        address tokenAddress = address(nuevoToken);
        if (supplyInicial > 0) {
            nuevoToken.mint(treasury, supplyInicial);
        }
        tokenDeProyecto[proyectoId] = tokenAddress;
        proyectoDeToken[tokenAddress] = proyectoId;
        tokensCreados.push(tokenAddress);
        emit TokenDeProyectoCreado(proyectoId, tokenAddress);
        return tokenAddress;
    }

    function invest(
        uint256 proyectoId,
        uint256 ideaAmount,
        uint256 subTokenAmount,
        address investor
    ) external nonReentrant {
        require(ideaAmount > 0, "InvestmentSwap: ideaAmount must be > 0");
        require(subTokenAmount > 0, "InvestmentSwap: subTokenAmount must be > 0");
        require(investor != address(0), "InvestmentSwap: invalid investor address");
        address projectTokenAddr = tokenDeProyecto[proyectoId];
        require(projectTokenAddr != address(0),
            "InvestmentSwap: project token not deployed");
        require(idea.transferFrom(msg.sender, treasury, ideaAmount),
            "InvestmentSwap: IDEA transfer failed (check allowance + balance)");
        ProjectToken(projectTokenAddr).mint(investor, subTokenAmount);
        emit InvestmentMade(proyectoId, investor, ideaAmount, subTokenAmount);
    }

    function refund(
        uint256 proyectoId,
        uint256 subTokenAmount,
        address holder,
        address investor
    ) external onlyOwner nonReentrant {
        require(subTokenAmount > 0, "InvestmentSwap: subTokenAmount must be > 0");
        address projectTokenAddr = tokenDeProyecto[proyectoId];
        require(projectTokenAddr != address(0),
            "InvestmentSwap: project token not deployed");
        ProjectToken(projectTokenAddr).burnFrom(holder, subTokenAmount);
        emit RefundMade(proyectoId, investor, 0, subTokenAmount);
    }

    function obtenerTokenDeProyecto(uint256 proyectoId) external view returns (address) {
        return tokenDeProyecto[proyectoId];
    }

    function obtenerProyectoDeToken(address tokenAddress) external view returns (uint256) {
        return proyectoDeToken[tokenAddress];
    }

    function obtenerCantidadTokens() external view returns (uint256) {
        return tokensCreados.length;
    }

    function obtenerTokenPorIndice(uint256 indice) external view returns (address) {
        require(indice < tokensCreados.length, "InvestmentSwap: index out of bounds");
        return tokensCreados[indice];
    }
}
