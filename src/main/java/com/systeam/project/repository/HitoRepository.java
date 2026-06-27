package com.systeam.project.repository;

import com.systeam.shared.model.Hito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HitoRepository extends JpaRepository<Hito, Long> {
    List<Hito> findByProyectoId(Long proyectoId);
}
