// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC721/extensions/ERC721Enumerable.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

contract SetBonusNFT is ERC721Enumerable, Ownable {

    enum Rarity { Common, Rare, Epic, Legendary }

    struct Piece {
        uint256 id;
        Rarity rarity;
        uint256 timestamp;
    }

    uint256 private _nextTokenId;
    mapping(uint256 => Piece) public pieces;

    event PieceMinted(address indexed to, uint256 indexed tokenId, Rarity rarity);

    constructor() ERC721("Ideafy Set Bonus", "ISB") Ownable(msg.sender) {}

    function mintRandom(address to, bytes32 entropy) external onlyOwner returns (uint256) {
        _nextTokenId++;
        uint256 tokenId = _nextTokenId;

        uint256 rand = uint256(entropy) % 100;
        Rarity rarity;

        if (rand < 60) {
            rarity = Rarity.Common; // 60%
        } else if (rand < 85) {
            rarity = Rarity.Rare; // 25%
        } else if (rand < 97) {
            rarity = Rarity.Epic; // 12%
        } else {
            rarity = Rarity.Legendary; // 3%
        }

        pieces[tokenId] = Piece({
            id: tokenId,
            rarity: rarity,
            timestamp: block.timestamp
        });

        _safeMint(to, tokenId);

        emit PieceMinted(to, tokenId, rarity);

        return tokenId;
    }

    function getPiece(uint256 tokenId) external view returns (Piece memory) {
        return pieces[tokenId];
    }
}
