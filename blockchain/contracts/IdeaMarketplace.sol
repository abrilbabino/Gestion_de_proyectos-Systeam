// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";

contract IdeaMarketplace is AccessControl, ReentrancyGuard {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    IERC20 public immutable idea;

    uint256 public feeBps = 250;

    struct Listing {
        address seller;
        address subToken;
        uint256 amount;
        uint256 pricePerToken;
        bool active;
    }

    uint256 public listingCounter;
    mapping(uint256 => Listing) public listings;
    mapping(address => uint256[]) public listingsOfSeller;

    event Listed(uint256 indexed listingId, address indexed seller, address indexed subToken, uint256 amount, uint256 pricePerToken);
    event TradeExecuted(uint256 indexed listingId, address indexed buyer, uint256 amount, uint256 totalPrice);
    event ListingCancelled(uint256 indexed listingId);

    constructor(address _idea) {
        require(_idea != address(0), "MP: invalid IDEA");
        idea = IERC20(_idea);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function listTokens(address subToken, uint256 amount, uint256 pricePerToken) external nonReentrant {
        require(amount > 0, "MP: amount > 0");
        require(pricePerToken > 0, "MP: price > 0");
        require(IERC20(subToken).balanceOf(msg.sender) >= amount, "MP: insufficient balance");

        IERC20(subToken).transferFrom(msg.sender, address(this), amount);

        listingCounter++;
        listings[listingCounter] = Listing({
            seller: msg.sender,
            subToken: subToken,
            amount: amount,
            pricePerToken: pricePerToken,
            active: true
        });
        listingsOfSeller[msg.sender].push(listingCounter);

        emit Listed(listingCounter, msg.sender, subToken, amount, pricePerToken);
    }

    function buyTokens(uint256 listingId, uint256 amount) external nonReentrant {
        Listing storage listing = listings[listingId];
        require(listing.active, "MP: listing not active");
        require(amount <= listing.amount, "MP: not enough tokens");
        require(amount > 0, "MP: amount > 0");

        uint256 totalPrice = amount * listing.pricePerToken;
        uint256 fee = (totalPrice * feeBps) / 10000;
        uint256 sellerProceeds = totalPrice - fee;

        require(idea.transferFrom(msg.sender, address(this), totalPrice),
            "MP: IDEA transfer failed");
        require(idea.transfer(listing.seller, sellerProceeds),
            "MP: seller transfer failed");

        IERC20(listing.subToken).transfer(msg.sender, amount);

        listing.amount -= amount;
        if (listing.amount == 0) {
            listing.active = false;
        }

        emit TradeExecuted(listingId, msg.sender, amount, totalPrice);
    }

    function cancelListing(uint256 listingId) external nonReentrant {
        Listing storage listing = listings[listingId];
        require(listing.seller == msg.sender, "MP: not your listing");
        require(listing.active, "MP: not active");

        listing.active = false;
        IERC20(listing.subToken).transfer(msg.sender, listing.amount);

        emit ListingCancelled(listingId);
    }

    function getSellerListings(address seller) external view returns (uint256[] memory) {
        return listingsOfSeller[seller];
    }

    function collectFees() external onlyRole(ADMIN_ROLE) {
        uint256 balance = idea.balanceOf(address(this));
        require(balance > 0, "MP: no fees to collect");
        idea.transfer(msg.sender, balance);
    }

    function updateFee(uint256 newFeeBps) external onlyRole(ADMIN_ROLE) {
        require(newFeeBps <= 1000, "MP: fee max 10%");
        feeBps = newFeeBps;
    }
}
