package com.systeam.investment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.systeam.shared.model.Inversion;

public interface InvestmentRepository {
    Inversion save(Inversion inversion);
    Optional<Inversion> findById(Long id);
    Page<Inversion> findByUsuarioId(Long usuarioId, Pageable pageable);
    List<Inversion> findByProyectoIdAndEstado(Long proyectoId, String estado);
    List<Inversion> findPendingRefundsByProyectoId(Long proyectoId);
}
