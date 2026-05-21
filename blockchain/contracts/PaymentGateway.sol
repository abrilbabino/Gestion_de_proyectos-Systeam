// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract PaymentGateway is Ownable {

    event Paid(uint256 indexed amount, bytes32 indexed actionId, address indexed payer);

    event Withdrawn(address indexed to, uint256 amount);

    IERC20 public usdc;
    address public treasury;

    constructor(address _usdc, address _treasury) Ownable(_treasury) {
        usdc = IERC20(_usdc);
        treasury = _treasury;
    }

    function pay(uint256 amount, bytes32 actionId) external {
        require(amount > 0, "El monto debe ser mayor a 0");
        require(usdc.balanceOf(msg.sender) >= amount, "Saldo USDC insuficiente");
        require(usdc.allowance(msg.sender, address(this)) >= amount,
            "Debe aprobar USDC al PaymentGateway primero");

        require(
            usdc.transferFrom(msg.sender, treasury, amount),
            "Fallo la transferencia de USDC"
        );

        emit Paid(amount, actionId, msg.sender);
    }

    function setTreasury(address nuevoTreasury) external onlyOwner {
        require(nuevoTreasury != address(0), "Treasury no puede ser zero address");
        treasury = nuevoTreasury;
    }

    function withdraw(uint256 amount) external onlyOwner {
        require(usdc.balanceOf(address(this)) >= amount, "Saldo insuficiente en el contrato");
        require(usdc.transfer(owner(), amount), "Fallo el retiro");
        emit Withdrawn(owner(), amount);
    }

    function getContractBalance() external view returns (uint256) {
        return usdc.balanceOf(address(this));
    }
}
