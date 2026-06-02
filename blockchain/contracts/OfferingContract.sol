// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./IdeafyFactory.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract OfferingContract is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    IERC20 public immutable idea;
    IdeafyFactory public immutable factory;

    struct Offering {
        uint256 proyectoId;
        address creator;
        uint256 softCap;
        uint256 hardCap;
        uint256 pricePerToken;
        uint256 basePrice;
        uint256 startTime;
        uint256 endTime;
        bool finalized;
        bool success;
        uint256 totalInvested;
    }

    mapping(uint256 => Offering) public offerings;
    mapping(uint256 => mapping(address => uint256)) public contributions;
    mapping(uint256 => mapping(address => uint256)) public tokenOwed;
    mapping(uint256 => address[]) public investors;

    event OfferingRegistered(uint256 indexed proyectoId, uint256 softCap, uint256 hardCap, uint256 pricePerToken);
    event InvestmentMade(uint256 indexed proyectoId, address indexed investor, uint256 ideaAmount, uint256 tokenAmount);
    event OfferingFinalized(uint256 indexed proyectoId, bool success);
    event TokensClaimed(uint256 indexed proyectoId, address indexed investor, uint256 amount);
    event RefundMade(uint256 indexed proyectoId, address indexed investor, uint256 amount);

    constructor(address _idea, address _factory) {
        require(_idea != address(0), "Offering: invalid IDEA");
        require(_factory != address(0), "Offering: invalid factory");
        idea = IERC20(_idea);
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function registerOffering(
        uint256 proyectoId,
        address creator,
        uint256 softCap,
        uint256 hardCap,
        uint256 pricePerToken,
        uint256 basePrice,
        uint256 startTime,
        uint256 endTime
    ) external onlyRole(ADMIN_ROLE) {
        require(offerings[proyectoId].proyectoId == 0, "Offering: already registered");
        require(softCap > 0, "Offering: softCap > 0");
        require(hardCap >= softCap, "Offering: hardCap >= softCap");
        require(pricePerToken > 0, "Offering: pricePerToken > 0");
        require(basePrice > 0 && basePrice <= pricePerToken, "Offering: 0 < basePrice <= pricePerToken");
        require(startTime < endTime, "Offering: startTime < endTime");
        require(endTime > block.timestamp, "Offering: endTime in future");
        require(creator != address(0), "Offering: invalid creator");
        require(factory.subTokenOfProject(proyectoId) != address(0),
            "Offering: project must have a token first");

        offerings[proyectoId] = Offering({
            proyectoId: proyectoId,
            creator: creator,
            softCap: softCap,
            hardCap: hardCap,
            pricePerToken: pricePerToken,
            basePrice: basePrice,
            startTime: startTime,
            endTime: endTime,
            finalized: false,
            success: false,
            totalInvested: 0
        });

        emit OfferingRegistered(proyectoId, softCap, hardCap, pricePerToken);
    }

    function updatePricePerToken(uint256 proyectoId, uint256 newPrice) external onlyRole(ADMIN_ROLE) {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        require(!off.finalized, "Offering: already finalized");
        require(block.timestamp >= off.startTime, "Offering: not started");
        require(block.timestamp <= off.endTime, "Offering: ended");
        require(newPrice >= off.basePrice, "Offering: price below basePrice");
        require(newPrice <= off.basePrice * 120 / 100, "Offering: price exceeds +20% max");

        off.pricePerToken = newPrice;
    }

    function invest(uint256 proyectoId, uint256 ideaAmount) external nonReentrant {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        require(!off.finalized, "Offering: already finalized");
        require(block.timestamp >= off.startTime, "Offering: not started");
        require(block.timestamp <= off.endTime, "Offering: ended");
        require(ideaAmount > 0, "Offering: amount > 0");
        require(off.totalInvested + ideaAmount <= off.hardCap,
            "Offering: exceeds hard cap");

        require(idea.transferFrom(msg.sender, address(this), ideaAmount),
            "Offering: IDEA transfer failed");

        uint256 tokenAmount = (ideaAmount * 1e18) / off.pricePerToken;

        if (contributions[proyectoId][msg.sender] == 0) {
            investors[proyectoId].push(msg.sender);
        }
        contributions[proyectoId][msg.sender] += ideaAmount;
        tokenOwed[proyectoId][msg.sender] += tokenAmount;
        off.totalInvested += ideaAmount;

        emit InvestmentMade(proyectoId, msg.sender, ideaAmount, tokenAmount);
    }

    function finalize(uint256 proyectoId) external onlyRole(ADMIN_ROLE) {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        require(!off.finalized, "Offering: already finalized");
        require(block.timestamp > off.endTime, "Offering: still active");

        off.finalized = true;

        if (off.totalInvested >= off.softCap) {
            off.success = true;
            require(idea.transfer(off.creator, off.totalInvested),
                "Offering: creator transfer failed");
        }

        emit OfferingFinalized(proyectoId, off.success);
    }

    function claimTokens(uint256 proyectoId) external nonReentrant {
        Offering storage off = offerings[proyectoId];
        require(off.finalized, "Offering: not finalized");
        require(off.success, "Offering: not successful");
        uint256 tokens = tokenOwed[proyectoId][msg.sender];
        require(tokens > 0, "Offering: nothing to claim");

        tokenOwed[proyectoId][msg.sender] = 0;
        contributions[proyectoId][msg.sender] = 0;

        factory.allocateTokens(proyectoId, msg.sender, tokens);

        emit TokensClaimed(proyectoId, msg.sender, tokens);
    }

    function refund(uint256 proyectoId) external nonReentrant {
        Offering storage off = offerings[proyectoId];
        require(off.finalized, "Offering: not finalized");
        require(!off.success, "Offering: was successful, use claimTokens");
        uint256 tokens = tokenOwed[proyectoId][msg.sender];
        require(tokens > 0, "Offering: nothing to refund");

        uint256 refundAmount = (off.basePrice * tokens) / 1e18;
        tokenOwed[proyectoId][msg.sender] = 0;
        contributions[proyectoId][msg.sender] = 0;
        require(idea.transfer(msg.sender, refundAmount),
            "Offering: refund transfer failed");

        emit RefundMade(proyectoId, msg.sender, refundAmount);
    }

    function getInvestors(uint256 proyectoId) external view returns (address[] memory) {
        return investors[proyectoId];
    }

    function getInvestorsPaginated(uint256 proyectoId, uint256 offset, uint256 limit)
        external view returns (address[] memory result, uint256 total)
    {
        address[] storage invs = investors[proyectoId];
        total = invs.length;
        if (offset >= total) return (new address[](0), total);
        uint256 end = offset + limit;
        if (end > total) end = total;
        uint256 resultCount = end - offset;
        result = new address[](resultCount);
        for (uint256 i = 0; i < resultCount; i++) {
            result[i] = invs[offset + i];
        }
    }

    function getContribution(uint256 proyectoId, address investor) external view returns (uint256) {
        return contributions[proyectoId][investor];
    }

    function getInvestorCount(uint256 proyectoId) external view returns (uint256) {
        return investors[proyectoId].length;
    }
}
