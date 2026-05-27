// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./SubToken.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract IdeafyFactory is AccessControl {

    bytes32 public constant CREATOR_ROLE = keccak256("CREATOR_ROLE");
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant ALLOCATOR_ROLE = keccak256("ALLOCATOR_ROLE");

    mapping(uint256 => address) public subTokenOfProject;
    mapping(address => uint256) public projectOfSubToken;
    mapping(address => uint256[]) public projectsOfCreator;
    address[] public allSubTokens;

    event ProjectLaunched(
        uint256 indexed proyectoId,
        address indexed subToken,
        address indexed creator,
        uint256 rubroId,
        uint256 dividendBps,
        uint256 supply
    );

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function launchProject(
        uint256 proyectoId,
        uint256 rubroId,
        uint256 dividendBps,
        address creator,
        string calldata nombre,
        string calldata simbolo,
        uint256 supplyInicial
    ) external onlyRole(CREATOR_ROLE) returns (address) {
        require(subTokenOfProject[proyectoId] == address(0),
            "IdeafyFactory: project already has a token");
        require(creator != address(0), "IdeafyFactory: invalid creator");
        require(supplyInicial > 0, "IdeafyFactory: supply must be > 0");

        SubToken nuevo = new SubToken(
            proyectoId,
            rubroId,
            dividendBps,
            creator,
            address(this),
            nombre,
            simbolo,
            supplyInicial
        );

        address tokenAddr = address(nuevo);
        subTokenOfProject[proyectoId] = tokenAddr;
        projectOfSubToken[tokenAddr] = proyectoId;
        projectsOfCreator[creator].push(proyectoId);
        allSubTokens.push(tokenAddr);

        emit ProjectLaunched(proyectoId, tokenAddr, creator, rubroId, dividendBps, supplyInicial);
        return tokenAddr;
    }

    function getProjectsOfCreator(address creator) external view returns (uint256[] memory) {
        return projectsOfCreator[creator];
    }

    function getSubTokenCount() external view returns (uint256) {
        return allSubTokens.length;
    }

    function getSubTokenAt(uint256 index) external view returns (address) {
        require(index < allSubTokens.length, "IdeafyFactory: index out of bounds");
        return allSubTokens[index];
    }

    function allocateTokens(
        uint256 proyectoId,
        address to,
        uint256 amount
    ) external onlyRole(ALLOCATOR_ROLE) {
        require(to != address(0), "IdeafyFactory: invalid recipient");
        require(amount > 0, "IdeafyFactory: amount must be > 0");
        SubToken sub = SubToken(subTokenOfProject[proyectoId]);
        require(address(sub) != address(0), "IdeafyFactory: project not found");
        sub.transfer(to, amount);
    }
}
