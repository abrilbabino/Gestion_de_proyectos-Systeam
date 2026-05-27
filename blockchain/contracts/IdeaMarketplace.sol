// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/utils/ReentrancyGuard.sol";
import "@openzeppelin/contracts/utils/cryptography/ECDSA.sol";
import "@openzeppelin/contracts/utils/cryptography/EIP712.sol";

contract IdeaMarketplace is AccessControl, ReentrancyGuard, EIP712 {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");

    IERC20 public immutable idea;
    uint256 public feeBps = 250;

    // Typehash para EIP-712 signing
    bytes32 public constant ORDER_TYPEHASH = keccak256(
        "Order(address seller,address subToken,uint256 amount,uint256 pricePerToken,uint256 nonce,uint256 expiry)"
    );

    // Nonces para ordenes off-chain (evita replay attacks)
    mapping(address => uint256) public nonces;

    // Listings on-chain (para casos simples)
    uint256 public listingCounter;
    mapping(uint256 => Listing) public listings;

    struct Listing {
        address seller;
        address subToken;
        uint256 amount;
        uint256 pricePerToken;
        bool active;
    }

    struct Order {
        address seller;
        address subToken;
        uint256 amount;
        uint256 pricePerToken;
        uint256 nonce;
        uint256 expiry;
    }

    event Listed(uint256 indexed listingId, address indexed seller, address indexed subToken, uint256 amount, uint256 pricePerToken);
    event TradeExecuted(uint256 indexed listingId, address indexed buyer, uint256 amount, uint256 totalPrice);
    event ListingCancelled(uint256 indexed listingId);
    event OrderExecuted(address indexed seller, address indexed buyer, address indexed subToken, uint256 amount, uint256 totalPrice);
    event FeeUpdated(uint256 oldFee, uint256 newFee);

    constructor(address _idea) EIP712("IDEAFY Marketplace", "1") {
        require(_idea != address(0), "MP: invalid IDEA");
        idea = IERC20(_idea);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    // ─────────────────────────────────────────────
    //  ON-CHAIN LISTINGS (para uso simple)
    // ─────────────────────────────────────────────

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

        emit Listed(listingCounter, msg.sender, subToken, amount, pricePerToken);
    }

    function buyTokens(uint256 listingId, uint256 amount) external nonReentrant {
        Listing storage listing = listings[listingId];
        require(listing.active, "MP: listing not active");
        require(amount <= listing.amount, "MP: not enough tokens");
        require(amount > 0, "MP: amount > 0");

        uint256 totalPrice = amount * listing.pricePerToken;
        _executeTrade(listing.seller, msg.sender, listing.subToken, amount, totalPrice);

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

    // ─────────────────────────────────────────────
    //  OFF-CHAIN ORDERS (para matching fuera de la blockchain)
    // ─────────────────────────────────────────────

    function executeOrder(Order calldata order, bytes calldata signature) external nonReentrant {
        require(order.amount > 0, "MP: amount > 0");
        require(order.pricePerToken > 0, "MP: price > 0");
        require(order.expiry >= block.timestamp, "MP: order expired");
        require(order.nonce == nonces[order.seller], "MP: invalid nonce");

        // Verificar firma EIP-712
        bytes32 digest = _hashTypedDataV4(keccak256(abi.encode(
            ORDER_TYPEHASH,
            order.seller,
            order.subToken,
            order.amount,
            order.pricePerToken,
            order.nonce,
            order.expiry
        )));
        address signer = ECDSA.recover(digest, signature);
        require(signer == order.seller, "MP: invalid signature");

        // Consumir nonce (protege contra replay)
        nonces[order.seller]++;

        uint256 totalPrice = order.amount * order.pricePerToken;
        _executeTrade(order.seller, msg.sender, order.subToken, order.amount, totalPrice);

        emit OrderExecuted(order.seller, msg.sender, order.subToken, order.amount, totalPrice);
    }

    // ─────────────────────────────────────────────
    //  INTERNAL
    // ─────────────────────────────────────────────

    function _executeTrade(address seller, address buyer, address subToken, uint256 amount, uint256 totalPrice) private {
        uint256 fee = (totalPrice * feeBps) / 10000;
        uint256 sellerProceeds = totalPrice - fee;

        require(idea.transferFrom(buyer, address(this), totalPrice),
            "MP: IDEA transfer failed");
        require(idea.transfer(seller, sellerProceeds),
            "MP: seller transfer failed");

        IERC20(subToken).transfer(buyer, amount);
    }

    // ─────────────────────────────────────────────
    //  VIEWS CON PAGINACION
    // ─────────────────────────────────────────────

    function getListingsPaginated(uint256 offset, uint256 limit)
        external view returns (Listing[] memory result, uint256 total)
    {
        total = listingCounter;
        if (offset >= total) return (new Listing[](0), total);
        uint256 end = offset + limit;
        if (end > total) end = total;
        uint256 resultCount = end - offset;
        result = new Listing[](resultCount);
        for (uint256 i = 0; i < resultCount; i++) {
            result[i] = listings[offset + i + 1];
        }
    }

    function getActiveListingsPaginated(uint256 offset, uint256 limit)
        external view returns (Listing[] memory result, uint256 totalActive)
    {
        // Primero contamos listings activos
        uint256 count = listingCounter;
        uint256 activeCount;
        for (uint256 i = 1; i <= count; i++) {
            if (listings[i].active) activeCount++;
        }
        totalActive = activeCount;

        if (offset >= activeCount) return (new Listing[](0), activeCount);

        // Buscamos desde el offset
        uint256 found;
        uint256 end = offset + limit;
        if (end > activeCount) end = activeCount;
        uint256 resultCount = end - offset;
        result = new Listing[](resultCount);

        for (uint256 i = 1; i <= count && found < end; i++) {
            if (listings[i].active) {
                if (found >= offset) {
                    result[found - offset] = listings[i];
                }
                found++;
            }
        }
    }

    function getListing(uint256 listingId) external view returns (Listing memory) {
        return listings[listingId];
    }

    // ─────────────────────────────────────────────
    //  ADMIN
    // ─────────────────────────────────────────────

    function collectFees() external onlyRole(ADMIN_ROLE) {
        uint256 balance = idea.balanceOf(address(this));
        require(balance > 0, "MP: no fees to collect");
        idea.transfer(msg.sender, balance);
    }

    function updateFee(uint256 newFeeBps) external onlyRole(ADMIN_ROLE) {
        require(newFeeBps <= 1000, "MP: fee max 10%");
        uint256 old = feeBps;
        feeBps = newFeeBps;
        emit FeeUpdated(old, newFeeBps);
    }
}
