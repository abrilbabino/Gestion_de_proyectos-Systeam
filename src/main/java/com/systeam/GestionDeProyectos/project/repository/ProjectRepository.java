package com.systeam.GestionDeProyectos.project.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.systeam.GestionDeProyectos.project.model.Project;
import com.systeam.GestionDeProyectos.project.model.ProjectStatus;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    Page<Project> findByCreatorId(Long creatorId, Pageable pageable);

    Page<Project> findByStatusIn(List<ProjectStatus> statuses, Pageable pageable);

    @Query("SELECT p FROM Project p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:search IS NULL OR LOWER(p.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Project> findByFilters(
        @Param("status") ProjectStatus status,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT p FROM Project p WHERE p.status IN ('FINANCIAMIENTO', 'EJECUCION')")
    List<Project> findActiveProjects();

    @Query("SELECT p FROM Project p WHERE p.status = 'FINANCIAMIENTO'")
    List<Project> findProjectsInFinancing();

    long countByStatus(ProjectStatus status);

    boolean existsByCreatorId(Long creatorId);
}
