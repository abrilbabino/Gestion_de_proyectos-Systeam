package com.systeam.GestionDeProyectos.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.systeam.shared.model.Proyecto;

public interface ProjectRepository {
    Proyecto save(Proyecto proyecto);
    Optional<Proyecto> findById(Long id);
    Page<Proyecto> findAll(Pageable pageable);
    Page<Proyecto> findByCreadorId(Long creadorId, Pageable pageable);
    Page<Proyecto> findByFilters(String estado, String search, Pageable pageable);
    List<Proyecto> findProjectsInFinancing();
}
