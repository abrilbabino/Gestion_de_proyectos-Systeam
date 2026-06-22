package com.systeam.project.service;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.blockchain.service.BlockchainService;
import com.systeam.blockchain.service.IdeaGovernanceService;
import com.systeam.project.exception.ConflictException;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.project.repository.ProjectRepository;
import com.systeam.project.repository.ProjectVoteRepository;
import com.systeam.rewards.service.RewardService;
import com.systeam.shared.model.Proyecto;
import com.systeam.voteeconomics.VoteEconomicsConfig;
import com.systeam.voteeconomics.VoteEconomicsConfigService;
import com.systeam.wallet.service.WalletService;

@Service
public class ProjectVoteService {

    private static final Logger log = LoggerFactory.getLogger(ProjectVoteService.class);

    private final ProjectRepository projectRepository;
    private final ProjectVoteRepository voteRepository;
    private final WalletService walletService;
    private final VoteEconomicsConfigService configService;
    private final RewardService rewardService;
    private final IdeaGovernanceService onChainService;
    private final BlockchainService blockchainService;
    private final ProjectVoteStreamRegistry streamRegistry;

    public ProjectVoteService(ProjectRepository projectRepository,
                              ProjectVoteRepository voteRepository,
                              WalletService walletService,
                              VoteEconomicsConfigService configService,
                              RewardService rewardService,
                              IdeaGovernanceService onChainService,
                              BlockchainService blockchainService,
                              ProjectVoteStreamRegistry streamRegistry) {
        this.projectRepository = projectRepository;
        this.voteRepository = voteRepository;
        this.walletService = walletService;
        this.configService = configService;
        this.rewardService = rewardService;
        this.onChainService = onChainService;
        this.blockchainService = blockchainService;
        this.streamRegistry = streamRegistry;
    }

    public void validateVote(Long userId, Long projectId) {
        Proyecto proyecto = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + projectId));

        if (!"EJECUCION".equals(proyecto.getEstado())) {
            throw new ConflictException("Solo se pueden votar proyectos en estado EJECUCION");
        }

        if (voteRepository.hasVoted(projectId, userId)) {
            throw new ConflictException("Ya votaste en este proyecto");
        }

        VoteEconomicsConfig config = configService.loadConfig();
        if (config.getVoteCost().signum() > 0) {
            walletService.checkSufficientBalance(userId, config.getVoteCost());
        }
    }

    public String vote(Long userId, Long projectId, boolean support) throws Exception {
        Long onChainProposalId = getOrCreateOnChainProposal(projectId);

        String txHash = onChainService.vote(BigInteger.valueOf(onChainProposalId), support);

        recordVote(userId, projectId, support, txHash);

        return txHash;
    }

    @Transactional
    public void recordVote(Long userId, Long projectId, boolean support, String txHash) {
        VoteEconomicsConfig config = configService.loadConfig();
        BigDecimal cost = config.getVoteCost();
        BigDecimal reward = config.getVoteReward();

        if (cost.signum() > 0) {
            walletService.adjustBalance(userId, cost.negate());
            if (config.getTreasuryUserId() != null) {
                walletService.adjustBalance(config.getTreasuryUserId(), cost);
            }
        }

        voteRepository.insertVote(projectId, userId, support, txHash);
        voteRepository.incrementVoteCounts(projectId, support);

        if (reward.signum() > 0) {
            rewardService.accrue(userId, "VOTE_REWARD", "PROJECT", projectId, txHash, reward);
        }

        long[] counts = voteRepository.getVoteCounts(projectId);
        streamRegistry.broadcast(projectId, counts[0], counts[1]);

        log.info("Project vote recorded: userId={} projectId={} support={} cost={} reward={} txHash={}",
                userId, projectId, support, cost, reward, txHash);
    }

    private synchronized Long getOrCreateOnChainProposal(Long projectId) throws Exception {
        Long existing = voteRepository.getOnChainProposalId(projectId);
        if (existing != null) {
            return existing;
        }

        Proyecto proyecto = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Proyecto no encontrado con ID: " + projectId));

        String txHash = onChainService.createProposal(
            "Project vote: " + proyecto.getTitulo(), 0, new byte[0]);

        if (!blockchainService.verifyTransaction(txHash)) {
            throw new RuntimeException("On-chain proposal creation failed for project " + projectId);
        }

        BigInteger onChainId = onChainService.getProposalCount();
        voteRepository.setOnChainProposalId(projectId, onChainId.longValue());

        return onChainId.longValue();
    }
}
