// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/access/AccessControl.sol";

contract IdeaGovernance is AccessControl {

    bytes32 public constant ADMIN_ROLE = keccak256("ADMIN_ROLE");
    bytes32 public constant EXECUTOR_ROLE = keccak256("EXECUTOR_ROLE");

    IERC20 public immutable idea;

    uint256 public constant PROPOSAL_THRESHOLD = 1000e18;
    uint256 public constant QUORUM_BPS = 1000;
    uint256 public constant VOTING_PERIOD = 7 days;

    enum ProposalType { ProjectApproval, ParameterChange, TreasuryAction }

    struct Proposal {
        uint256 id;
        address proposer;
        string description;
        ProposalType proposalType;
        bytes data;
        uint256 startTime;
        uint256 endTime;
        uint256 forVotes;
        uint256 againstVotes;
        bool executed;
    }

    uint256 public proposalCounter;
    mapping(uint256 => Proposal) public proposals;
    mapping(uint256 => mapping(address => bool)) public hasVoted;
    mapping(address => uint256) public governanceMultiplier;

    event ProposalCreated(uint256 indexed id, address indexed proposer, string description, ProposalType proposalType);
    event Voted(uint256 indexed proposalId, address indexed voter, bool support, uint256 weight);
    event ProposalExecuted(uint256 indexed id);
    event GovernanceMultiplierChanged(address indexed voter, uint256 multiplier);

    constructor(address _idea) {
        require(_idea != address(0), "GV: invalid IDEA");
        idea = IERC20(_idea);
        _grantRole(DEFAULT_ADMIN_ROLE, msg.sender);
        _grantRole(ADMIN_ROLE, msg.sender);
    }

    function createProposal(
        string calldata description,
        ProposalType proposalType,
        bytes calldata data
    ) external {
        require(idea.balanceOf(msg.sender) >= PROPOSAL_THRESHOLD,
            "GV: need 1000 IDEA to propose");

        proposalCounter++;
        proposals[proposalCounter] = Proposal({
            id: proposalCounter,
            proposer: msg.sender,
            description: description,
            proposalType: proposalType,
            data: data,
            startTime: block.timestamp,
            endTime: block.timestamp + VOTING_PERIOD,
            forVotes: 0,
            againstVotes: 0,
            executed: false
        });

        emit ProposalCreated(proposalCounter, msg.sender, description, proposalType);
    }

    function setGovernanceMultiplier(address voter, uint256 multiplier) external onlyRole(ADMIN_ROLE) {
        require(voter != address(0), "GV: invalid voter");
        governanceMultiplier[voter] = multiplier;
        emit GovernanceMultiplierChanged(voter, multiplier);
    }

    function vote(uint256 proposalId, bool support) external {
        Proposal storage prop = proposals[proposalId];
        require(prop.id != 0, "GV: proposal not found");
        require(block.timestamp >= prop.startTime, "GV: voting not started");
        require(block.timestamp <= prop.endTime, "GV: voting ended");
        require(!hasVoted[proposalId][msg.sender], "GV: already voted");

        uint256 weight = idea.balanceOf(msg.sender);
        uint256 mult = governanceMultiplier[msg.sender];
        if (mult > 1) {
            weight = weight * mult;
        }
        require(weight > 0, "GV: need IDEA to vote");

        hasVoted[proposalId][msg.sender] = true;

        if (support) {
            prop.forVotes += weight;
        } else {
            prop.againstVotes += weight;
        }

        emit Voted(proposalId, msg.sender, support, weight);
    }

    function executeProposal(uint256 proposalId) external onlyRole(EXECUTOR_ROLE) {
        Proposal storage prop = proposals[proposalId];
        require(prop.id != 0, "GV: proposal not found");
        require(block.timestamp > prop.endTime, "GV: voting still active");
        require(!prop.executed, "GV: already executed");

        uint256 totalVotes = prop.forVotes + prop.againstVotes;
        uint256 totalSupply = IERC20(idea).totalSupply();
        require(totalVotes >= (totalSupply * QUORUM_BPS) / 10000, "GV: quorum not met");
        require(prop.forVotes > prop.againstVotes, "GV: proposal defeated");

        prop.executed = true;

        (bool success, ) = address(this).call(prop.data);
        require(success, "GV: execution failed");

        emit ProposalExecuted(proposalId);
    }

    function getProposal(uint256 proposalId) external view returns (Proposal memory) {
        return proposals[proposalId];
    }
}
