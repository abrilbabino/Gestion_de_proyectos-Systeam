// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract MockUSDC is ERC20, Ownable {
    constructor() ERC20("Mock USD Coin", "USDC") Ownable(msg.sender) {
        // Mint 100 Million USDC to the deployer for testing
        // Standard USDC has 6 decimals, but we'll use 18 for simplicity in the MVP to match $IDEA.
        _mint(msg.sender, 100_000_000 * 10 ** decimals());
    }

    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }
}
