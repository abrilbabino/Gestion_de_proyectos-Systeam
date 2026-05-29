package com.systeam.tokenization.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.systeam.blockchain.service.IdeafyFactoryService;
import com.systeam.blockchain.service.InvestmentSwapService;
import com.systeam.tokenization.service.TokenFactoryService;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.tokenization.dto.CreateTokenRequest;
import com.systeam.tokenization.dto.TokenResponse;
import com.systeam.tokenization.repository.TokenizationRepository;

@Service
public class TokenizationService {

    private static final Logger log = LoggerFactory.getLogger(TokenizationService.class);

    private final IdeafyFactoryService ideafyFactoryService;
    private final InvestmentSwapService investmentSwapService;
    private final TokenFactoryService tokenFactoryService;
    private final TokenizationRepository tokenizationRepository;

    public TokenizationService(IdeafyFactoryService ideafyFactoryService,
                               InvestmentSwapService investmentSwapService,
                               TokenFactoryService tokenFactoryService,
                               TokenizationRepository tokenizationRepository) {
        this.ideafyFactoryService = ideafyFactoryService;
        this.investmentSwapService = investmentSwapService;
        this.tokenFactoryService = tokenFactoryService;
        this.tokenizationRepository = tokenizationRepository;
    }

    @Transactional
    public String crearTokenParaProyecto(Long proyectoId, String titulo, Integer cupoMaximoTokens, BigDecimal valorNominal) {
        String tokenName = titulo.length() > 20 ? titulo.substring(0, 17) + "..." : titulo;
        String tokenSymbol = "p" + proyectoId.toString().substring(0, Math.min(4, proyectoId.toString().length()));
        int supply = cupoMaximoTokens != null ? cupoMaximoTokens : 100000;
        BigInteger supplyInicial = BigInteger.valueOf(supply);

        String contractAddress = null;

        // 1. Intentar con IdeafyFactory (contrato nuevo, upgradeable)
        if (contractAddress == null) {
            try {
                contractAddress = ideafyFactoryService.obtenerTokenDeProyecto(proyectoId);
                if (contractAddress == null) {
                    contractAddress = ideafyFactoryService.launchProject(
                        proyectoId, 1, 4000,
                        "0x0000000000000000000000000000000000000000",
                        tokenName, tokenSymbol, supplyInicial
                    );
                    log.info("Token creado via IdeafyFactory para proyecto {}: {} -> {}",
                        proyectoId, tokenSymbol, contractAddress);
                } else {
                    log.info("Token ya existia en IdeafyFactory para proyecto {}: {}", proyectoId, contractAddress);
                }
            } catch (Exception e) {
                log.warn("IdeafyFactory no disponible: {}. Probando InvestmentSwap.", e.getMessage());
            }
        }

        // 2. Fallback a InvestmentSwap (contrato legacy)
        if (contractAddress == null) {
            try {
                String existing = investmentSwapService.obtenerTokenDeProyecto(proyectoId);
                if (existing == null || existing.equals("0x0000000000000000000000000000000000000000")) {
                    contractAddress = investmentSwapService.crearTokenProyecto(
                        proyectoId, tokenName, tokenSymbol, supplyInicial
                    );
                    log.info("Token creado via InvestmentSwap para proyecto {}: {} -> {}",
                        proyectoId, tokenSymbol, contractAddress);
                } else {
                    contractAddress = existing;
                    log.info("Token ya existia en InvestmentSwap para proyecto {}: {}", proyectoId, contractAddress);
                }
            } catch (Exception e) {
                log.warn("InvestmentSwap no disponible para proyecto {}: {}. Usando TokenFactory como fallback.",
                    proyectoId, e.getMessage());
            }
        }

        // 3. Fallback a TokenFactory (último recurso legacy)
        if (contractAddress == null) {
            try {
                contractAddress = tokenFactoryService.crearTokenProyecto(
                    proyectoId, tokenName, tokenSymbol, supplyInicial
                );
                log.info("Token creado via TokenFactory (fallback) para proyecto {}: {} -> {}",
                    proyectoId, tokenSymbol, contractAddress);
            } catch (Exception e2) {
                log.error("Error creando token para proyecto {}: {}. Usando address cero.",
                    proyectoId, e2.getMessage());
                contractAddress = "0x0000000000000000000000000000000000000000";
            }
        }

        BigDecimal valorNominalFinal = valorNominal != null ? valorNominal : BigDecimal.ONE;

        tokenizationRepository.save(proyectoId, tokenName, supply, valorNominalFinal,
            new BigDecimal("0.50"), contractAddress);

        log.info("Subtoken creado en DB para proyecto {}: {} (supply={}, precio={})",
            proyectoId, tokenSymbol, supply, valorNominalFinal);

        return contractAddress;
    }

    @Transactional
    public TokenResponse crearToken(CreateTokenRequest request) {
        String titulo = obtenerTituloProyecto(request.getProyectoId());
        String contractAddress = crearTokenParaProyecto(
            request.getProyectoId(), titulo,
            request.getCupoMaximoTokens(), request.getValorNominal()
        );
        Map<String, Object> row = tokenizationRepository.findByProjectId(request.getProyectoId())
            .orElseThrow(() -> new ResourceNotFoundException("Token no encontrado tras creacion"));
        return toResponse(row);
    }

    public TokenResponse obtenerTokenPorProyecto(Long proyectoId) {
        Map<String, Object> row = tokenizationRepository.findByProjectId(proyectoId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "No se encontro token para el proyecto: " + proyectoId));
        return toResponse(row);
    }

    public String obtenerDireccionToken(Long proyectoId) {
        try {
            String addr = ideafyFactoryService.obtenerTokenDeProyecto(proyectoId);
            if (addr != null) return addr;
        } catch (Exception e) {
            log.warn("No se pudo obtener token de IdeafyFactory: {}", e.getMessage());
        }
        try {
            String addr = investmentSwapService.obtenerTokenDeProyecto(proyectoId);
            if (addr != null && !addr.equals("0x0000000000000000000000000000000000000000")) {
                return addr;
            }
        } catch (Exception e) {
            log.warn("No se pudo obtener token de InvestmentSwap: {}", e.getMessage());
        }
        try {
            return tokenFactoryService.obtenerTokenDeProyecto(proyectoId);
        } catch (Exception e) {
            log.warn("No se pudo obtener token de TokenFactory: {}", e.getMessage());
            return null;
        }
    }

    public Page<TokenResponse> listarTokens(Pageable pageable) {
        return tokenizationRepository.findAll(pageable).map(this::toResponse);
    }

    private String obtenerTituloProyecto(Long proyectoId) {
        return "Proyecto #" + proyectoId;
    }

    private TokenResponse toResponse(Map<String, Object> row) {
        TokenResponse.TokenResponseBuilder builder = TokenResponse.builder()
            .id(toLong(row.get("id")))
            .proyectoId(toLong(row.get("proyecto_id")))
            .proyectoTitulo((String) row.get("proyectoTitulo"))
            .nombre((String) row.get("nombre"))
            .suministroTotal(toInt(row.get("suministro_total")))
            .cupoRestante(toInt(row.get("cupo_restante")))
            .precioActual(toBigDecimal(row.get("precio_actual")))
            .precioBase(toBigDecimal(row.get("precio_base")))
            .factorVolatilidad(toBigDecimal(row.get("factor_volatilidad")))
            .contractAddress((String) row.get("contract_address"));

        if (row.get("created_at") instanceof Timestamp ts) {
            builder.createdAt(ts.toLocalDateTime());
        }
        if (row.get("updated_at") instanceof Timestamp ts) {
            builder.updatedAt(ts.toLocalDateTime());
        }

        return builder.build();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.longValue();
        return Long.valueOf(val.toString());
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        return Integer.valueOf(val.toString());
    }

    private BigDecimal toBigDecimal(Object val) {
        if (val == null) return null;
        if (val instanceof BigDecimal bd) return bd;
        return new BigDecimal(val.toString());
    }
}
