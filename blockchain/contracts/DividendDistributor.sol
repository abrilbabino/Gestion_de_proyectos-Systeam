// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./IdeafyFactory.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract DividendDistributor is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    IERC20 public immutable idea;
    IdeafyFactory public immutable factory;

    // proyectoId => accumulated $IDEA per token (scaled by 1e18)
    mapping(uint256 => uint256) public dividendPerToken;

    // proyectoId => user => snapshot of dividendPerToken at last claim
    mapping(uint256 => mapping(address => uint256)) public dividendPerTokenPaid;

    // proyectoId => user => pending $IDEA (updated on transfer hooks)
    mapping(uint256 => mapping(address => uint256)) public pendingDividends;

    event Distributed(uint256 indexed proyectoId, uint256 totalAmount, uint256 perToken);
    event Claimed(uint256 indexed proyectoId, address indexed user, uint256 amount);

    constructor(address _idea, address _factory) {
        require(_idea != address(0), "DD: invalid IDEA");
        require(_factory != address(0), "DD: invalid factory");
        idea = IERC20(_idea);
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function distribute(uint256 proyectoId, uint256 totalDividend)
        external
        onlyRole(ADMIN_ROLE)
        nonReentrant
    {
        require(totalDividend > 0, "DD: amount > 0");

        require(idea.transferFrom(msg.sender, address(this), totalDividend),
            "DD: IDEA transfer failed");

        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        uint256 totalSupply = IERC20(subTokenAddr).totalSupply();
        require(totalSupply > 0, "DD: no tokens in circulation");

        dividendPerToken[proyectoId] += (totalDividend * 1e18) / totalSupply;

        emit Distributed(proyectoId, totalDividend, dividendPerToken[proyectoId]);
    }

    function claim(uint256 proyectoId) external nonReentrant {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        uint256 balance = IERC20(subTokenAddr).balanceOf(msg.sender);
        require(balance > 0, "DD: no tokens");

        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][msg.sender];

        uint256 owed = ((perToken - paid) * balance) / 1e18;

        if (owed == 0) {
            revert("DD: nothing to claim");
        }

        dividendPerTokenPaid[proyectoId][msg.sender] = perToken;
        pendingDividends[proyectoId][msg.sender] = 0;

        require(idea.transfer(msg.sender, owed), "DD: transfer failed");

        emit Claimed(proyectoId, msg.sender, owed);
    }

    function getClaimable(uint256 proyectoId, address user) external view returns (uint256) {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        if (subTokenAddr == address(0)) return 0;
        uint256 balance = IERC20(subTokenAddr).balanceOf(user);
        if (balance == 0) return 0;
        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][user];
        if (perToken <= paid) return 0;
        return ((perToken - paid) * balance) / 1e18;
    }
}
