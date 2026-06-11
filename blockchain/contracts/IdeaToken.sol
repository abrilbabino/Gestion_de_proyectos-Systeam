// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/extensions/ERC20Burnable.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract IdeaToken is ERC20Burnable, AccessControl {

    bytes32 public constant TREASURY_ROLE = keccak256("TREASURY_ROLE");

    uint256 public constant MAX_SUPPLY = 100_000_000e18;
    uint256 public constant BURN_BPS = 1;

    constructor() ERC20("IDEAFY Platform", "IDEA") {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(TREASURY_ROLE, msg.sender);
        _mint(msg.sender, MAX_SUPPLY);
    }

    function _update(address from, address to, uint256 amount) internal override {
        if (from != address(0) && to != address(0)) {
            uint256 burn = (amount * BURN_BPS) / 10000;
            if (burn > 0) {
                super._update(from, address(0), burn);
                super._update(from, to, amount - burn);
            } else {
                super._update(from, to, amount);
            }
        } else {
            super._update(from, to, amount);
        }
    }
}
