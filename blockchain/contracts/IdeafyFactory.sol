// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "./SubToken.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";
import "@openzeppelin/contracts/proxy/ERC1967/ERC1967Proxy.sol";

contract IdeafyFactory is AccessControl {

    bytes32 public constant CREATOR_ROLE = keccak256("CREATOR_ROLE");
    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant ALLOCATOR_ROLE = keccak256("ALLOCATOR_ROLE");

    address public subTokenImplementation;

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
    event ImplementationUpdated(address indexed newImplementation);

    constructor() {
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function setSubTokenImplementation(address _impl) external onlyRole(ADMIN_ROLE) {
        require(_impl != address(0), "Factory: invalid implementation");
        subTokenImplementation = _impl;
        emit ImplementationUpdated(_impl);
    }

    function launchProject(
        uint256 proyectoId,
        uint256 rubroId,
        uint256 dividendBps,
        address creator,
        string calldata nombre,
        string calldata simbolo,
        uint256 supplyInicial
    ) external onlyRole(CREATOR_ROLE) returns (address tokenAddr) {
        require(subTokenImplementation != address(0),
            "IdeafyFactory: implementation not set");
        require(subTokenOfProject[proyectoId] == address(0),
            "IdeafyFactory: project already has a token");
        require(creator != address(0), "IdeafyFactory: invalid creator");
        require(supplyInicial > 0, "IdeafyFactory: supply must be > 0");

        tokenAddr = address(new ERC1967Proxy(
            subTokenImplementation,
            abi.encodeWithSelector(
                SubToken.initialize.selector,
                proyectoId,
                rubroId,
                dividendBps,
                creator,
                address(this),
                nombre,
                simbolo,
                supplyInicial
            )
        ));

        subTokenOfProject[proyectoId] = tokenAddr;
        projectOfSubToken[tokenAddr] = proyectoId;
        projectsOfCreator[creator].push(proyectoId);
        allSubTokens.push(tokenAddr);

        emit ProjectLaunched(proyectoId, tokenAddr, creator, rubroId, dividendBps, supplyInicial);
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

    function getSubTokensPaginated(uint256 offset, uint256 limit)
        external view returns (address[] memory tokens)
    {
        uint256 count = allSubTokens.length;
        if (offset >= count) return new address[](0);
        uint256 end = offset + limit;
        if (end > count) end = count;
        uint256 resultCount = end - offset;
        tokens = new address[](resultCount);
        for (uint256 i = 0; i < resultCount; i++) {
            tokens[i] = allSubTokens[offset + i];
        }
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
