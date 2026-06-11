// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./IdeafyFactory.sol";
import "./SubToken.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract OfferingContract is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    IERC20 public immutable idea;
    IdeafyFactory public immutable factory;

    address public treasury;
    uint256 public constant ISSUANCE_FEE_BPS = 500;
    uint256 public constant MAX_INVESTORS = 500;
    uint256 public constant GRACE_PERIOD = 30 days;

    struct Offering {
        uint256 proyectoId;
        address creator;
        uint256 softCap;
        uint256 hardCap;
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

    event OfferingRegistered(uint256 indexed proyectoId, uint256 softCap, uint256 hardCap, uint256 basePrice);
    event InvestmentMade(uint256 indexed proyectoId, address indexed investor, uint256 ideaAmount, uint256 tokenAmount, uint256 price);
    event OfferingFinalized(uint256 indexed proyectoId, bool success);
    event TokensClaimed(uint256 indexed proyectoId, address indexed investor, uint256 amount);
    event RefundMade(uint256 indexed proyectoId, address indexed investor, uint256 amount);
    event TreasurySet(address indexed treasury);

    constructor(address _idea, address _factory) {
        require(_idea != address(0), "Offering: invalid IDEA");
        require(_factory != address(0), "Offering: invalid factory");
        idea = IERC20(_idea);
        factory = IdeafyFactory(_factory);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
        treasury = msg.sender;
    }

    function setTreasury(address _treasury) external onlyRole(ADMIN_ROLE) {
        require(_treasury != address(0), "Offering: invalid treasury");
        treasury = _treasury;
        emit TreasurySet(_treasury);
    }

    function registerOffering(
        uint256 proyectoId,
        address creator,
        uint256 softCap,
        uint256 hardCap,
        uint256 basePrice,
        uint256 startTime,
        uint256 endTime
    ) external {
        require(msg.sender == creator || hasRole(ADMIN_ROLE, msg.sender),
            "Offering: not authorized");
        require(offerings[proyectoId].proyectoId == 0, "Offering: already registered");
        require(softCap > 0, "Offering: softCap > 0");
        require(hardCap >= softCap, "Offering: hardCap >= softCap");
        require(basePrice > 0, "Offering: basePrice > 0");
        require(startTime < endTime, "Offering: startTime < endTime");
        require(endTime > block.timestamp, "Offering: endTime in future");
        require(creator != address(0), "Offering: invalid creator");
        address subToken = factory.subTokenOfProject(proyectoId);
        require(subToken != address(0),
            "Offering: project must have a token first");
        require(SubToken(subToken).creator() == creator,
            "Offering: creator mismatch");

        offerings[proyectoId] = Offering({
            proyectoId: proyectoId,
            creator: creator,
            softCap: softCap,
            hardCap: hardCap,
            basePrice: basePrice,
            startTime: startTime,
            endTime: endTime,
            finalized: false,
            success: false,
            totalInvested: 0
        });

        emit OfferingRegistered(proyectoId, softCap, hardCap, basePrice);
    }

    /// @notice Calcula el precio dinámico según el progreso de recaudación
    ///         Progreso <= 70%: precio = basePrice
    ///         Progreso > 70%: precio sube linealmente hasta max +20% al 100%
    function getCurrentPrice(uint256 proyectoId) public view returns (uint256) {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        return _calculatePrice(off.totalInvested, off.softCap, off.basePrice);
    }

    function _calculatePrice(uint256 totalInvested, uint256 softCap, uint256 basePrice) private pure returns (uint256) {
        if (softCap == 0) return basePrice;

        uint256 progressBps = (totalInvested * 10000) / softCap;

        if (progressBps <= 7000) {
            return basePrice;
        }

        uint256 excessBps = progressBps - 7000;
        if (excessBps > 3000) excessBps = 3000;

        uint256 premiumBps = (excessBps * 2000) / 3000;

        return basePrice + (basePrice * premiumBps / 10000);
    }

    function invest(uint256 proyectoId, uint256 ideaAmount) external nonReentrant {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        require(!off.finalized, "Offering: already finalized");
        require(block.timestamp >= off.startTime, "Offering: not started");
        require(block.timestamp <= off.endTime, "Offering: ended");
        require(ideaAmount > 0, "Offering: amount > 0");

        uint256 newTotal = off.totalInvested + ideaAmount;
        require(newTotal <= off.hardCap, "Offering: exceeds hard cap");

        uint256 effectivePrice = _calculatePrice(off.totalInvested, off.softCap, off.basePrice);

        uint256 tokenAmount = (ideaAmount * 1e18) / effectivePrice;
        require(tokenAmount > 0, "Offering: tokenAmount must be > 0");

        require(idea.transferFrom(msg.sender, address(this), ideaAmount),
            "Offering: IDEA transfer failed");

        if (contributions[proyectoId][msg.sender] == 0) {
            require(investors[proyectoId].length < MAX_INVESTORS,
                "Offering: max investors reached");
            investors[proyectoId].push(msg.sender);
        }
        contributions[proyectoId][msg.sender] += ideaAmount;
        tokenOwed[proyectoId][msg.sender] += tokenAmount;
        off.totalInvested = newTotal;

        emit InvestmentMade(proyectoId, msg.sender, ideaAmount, tokenAmount, effectivePrice);
    }

    function finalize(uint256 proyectoId) external onlyRole(ADMIN_ROLE) {
        _finalize(proyectoId, msg.sender);
    }

    function emergencyFinalize(uint256 proyectoId) external nonReentrant {
        require(block.timestamp > offerings[proyectoId].endTime + GRACE_PERIOD,
            "Offering: grace period not elapsed");
        _finalize(proyectoId, msg.sender);
    }

    function _finalize(uint256 proyectoId, address caller) private {
        Offering storage off = offerings[proyectoId];
        require(off.proyectoId != 0, "Offering: not registered");
        require(!off.finalized, "Offering: already finalized");
        require(block.timestamp > off.endTime, "Offering: still active");

        off.finalized = true;

        if (off.totalInvested >= off.softCap) {
            off.success = true;
            require(treasury != address(0), "Offering: treasury not set");
            uint256 fee = (off.totalInvested * ISSUANCE_FEE_BPS) / 10000;
            uint256 netAmount = off.totalInvested - fee;
            require(idea.transfer(off.creator, netAmount),
                "Offering: creator transfer failed");
            if (fee > 0) {
                require(idea.transfer(treasury, fee),
                    "Offering: fee transfer failed");
            }
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
