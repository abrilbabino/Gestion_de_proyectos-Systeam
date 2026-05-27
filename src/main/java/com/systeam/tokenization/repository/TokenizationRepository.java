package com.systeam.tokenization.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TokenizationRepository {

    Long save(Long proyectoId, String nombre, int suministroTotal, java.math.BigDecimal precioBase,
              java.math.BigDecimal factorVolatilidad, String contractAddress);

    Optional<Map<String, Object>> findById(Long id);

    Optional<Map<String, Object>> findByProjectId(Long proyectoId);

    List<Map<String, Object>> findAll();

    Page<Map<String, Object>> findAll(Pageable pageable);
}
