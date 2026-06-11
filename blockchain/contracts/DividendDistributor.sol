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

    address public treasury;
    uint256 public constant DISTRIBUTION_FEE_BPS = 50;

    // proyectoId => accumulated $IDEA per token (scaled by 1e18)
    mapping(uint256 => uint256) public dividendPerToken;

    // proyectoId => user => snapshot of dividendPerToken at last claim
    mapping(uint256 => mapping(address => uint256)) public dividendPerTokenPaid;

    // proyectoId => user => pending $IDEA credited via transfer hooks
    mapping(uint256 => mapping(address => uint256)) public pendingDividends;

    event Distributed(uint256 indexed proyectoId, uint256 totalAmount, uint256 perToken);
    event Claimed(uint256 indexed proyectoId, address indexed user, uint256 amount);
    event TreasurySet(address indexed treasury);

    constructor(address _idea, address _factory) {
        require(_idea != address(0), "DD: invalid IDEA");
        require(_factory != address(0), "DD: invalid factory");
        idea = IERC20(_idea);
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
        treasury = msg.sender;
    }

    function setTreasury(address _treasury) external onlyRole(ADMIN_ROLE) {
        require(_treasury != address(0), "DD: invalid treasury");
        treasury = _treasury;
        emit TreasurySet(_treasury);
    }

    function distribute(uint256 proyectoId, uint256 totalDividend)
        external
        onlyRole(ADMIN_ROLE)
        nonReentrant
    {
        require(totalDividend > 0, "DD: amount > 0");
        require(treasury != address(0), "DD: treasury not set");

        uint256 fee = (totalDividend * DISTRIBUTION_FEE_BPS) / 10000;
        uint256 netDividend = totalDividend - fee;

        require(idea.transferFrom(msg.sender, address(this), totalDividend),
            "DD: IDEA transfer failed");
        if (fee > 0) {
            require(idea.transfer(treasury, fee), "DD: fee transfer failed");
        }

        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        uint256 totalSupply = IERC20(subTokenAddr).totalSupply();
        require(totalSupply > 0, "DD: no tokens in circulation");

        dividendPerToken[proyectoId] += (netDividend * 1e18) / totalSupply;

        emit Distributed(proyectoId, netDividend, dividendPerToken[proyectoId]);
    }

    function claim(uint256 proyectoId) external nonReentrant {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        require(subTokenAddr != address(0), "DD: project not found");

        uint256 balance = IERC20(subTokenAddr).balanceOf(msg.sender);
        require(balance > 0, "DD: no tokens");

        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][msg.sender];

        uint256 owed = ((perToken - paid) * balance) / 1e18;
        owed += pendingDividends[proyectoId][msg.sender];

        if (owed == 0) {
            revert("DD: nothing to claim");
        }

        dividendPerTokenPaid[proyectoId][msg.sender] = perToken;
        pendingDividends[proyectoId][msg.sender] = 0;

        require(idea.transfer(msg.sender, owed), "DD: transfer failed");

        emit Claimed(proyectoId, msg.sender, owed);
    }

    /// @notice Hook llamado por SubToken antes de cada transferencia
    ///         Actualiza dividendPerTokenPaid para evitar doble reclamo
    function onTransfer(uint256 proyectoId, address from, address to, uint256 amount) external {
        address subToken = factory.subTokenOfProject(proyectoId);
        require(msg.sender == subToken, "DD: not subToken");

        uint256 perToken = dividendPerToken[proyectoId];
        if (perToken == 0) return;

        uint256 senderPaid = dividendPerTokenPaid[proyectoId][from];
        if (perToken > senderPaid) {
            uint256 fromBalance = IERC20(msg.sender).balanceOf(from);
            uint256 totalBalance = fromBalance + amount;
            uint256 senderEarned = ((perToken - senderPaid) * totalBalance) / 1e18;
            if (senderEarned > 0) {
                pendingDividends[proyectoId][from] += senderEarned;
            }
        }

        dividendPerTokenPaid[proyectoId][from] = perToken;
        dividendPerTokenPaid[proyectoId][to] = perToken;
    }

    function getClaimable(uint256 proyectoId, address user) external view returns (uint256) {
        address subTokenAddr = factory.subTokenOfProject(proyectoId);
        if (subTokenAddr == address(0)) return 0;
        uint256 balance = IERC20(subTokenAddr).balanceOf(user);
        if (balance == 0) return 0;
        uint256 perToken = dividendPerToken[proyectoId];
        uint256 paid = dividendPerTokenPaid[proyectoId][user];
        if (perToken <= paid) return pendingDividends[proyectoId][user];
        return ((perToken - paid) * balance) / 1e18 + pendingDividends[proyectoId][user];
    }
}
