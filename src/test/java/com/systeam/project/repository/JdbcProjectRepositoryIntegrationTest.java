package com.systeam.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.systeam.shared.model.Proyecto;
import com.systeam.shared.model.Usuario;
import com.systeam.testutil.TestDatabaseHelper;

class JdbcProjectRepositoryIntegrationTest {

    private static JdbcTemplate jdbc;
    private static DataSource ds;
    private JdbcProjectRepository repository;

    @BeforeAll
    static void init() {
        ds = TestDatabaseHelper.createDataSource();
        jdbc = new JdbcTemplate(ds);
        TestDatabaseHelper.executeSchema(jdbc);
    }

    @AfterAll
    static void cleanup() {
        TestDatabaseHelper.closeDataSource(ds);
    }

    @BeforeEach
    void setUp() {
        repository = new JdbcProjectRepository(jdbc);
        jdbc.execute("DELETE FROM projects");
        jdbc.execute("DELETE FROM users");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (1, 'Creador1')");
        jdbc.execute("INSERT INTO users (id, nombre) VALUES (2, 'Creador2')");
    }

    @Test
    void save_shouldUpdateExistingProject() {
        insertProject(1, 1, "Original", "PREPARACION", new BigDecimal("5000"));

        Proyecto p = createProject(1, "Original", "PREPARACION", new BigDecimal("5000"));
        p.setId(1L);
        p.setTitulo("Updated Title");
        p.setEstado("FINANCIAMIENTO");
        Proyecto updated = repository.save(p);

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getTitulo()).isEqualTo("Updated Title");
        assertThat(updated.getEstado()).isEqualTo("FINANCIAMIENTO");
    }

    @Test
    void save_shouldPersistOptionalFields() {
        insertProject(1, 1, "Full Project", "FINANCIAMIENTO", new BigDecimal("50000"));

        Proyecto p = createProject(1, "Full Project", "FINANCIAMIENTO", new BigDecimal("50000"));
        p.setId(1L);
        p.setCupoMaximoTokens(1000);
        p.setValorNominalToken(new BigDecimal("50.00"));
        p.setMontoRecaudado(new BigDecimal("25000"));
        p.setGobernanzaComunidad(true);
        Proyecto saved = repository.save(p);

        assertThat(saved.getCupoMaximoTokens()).isEqualTo(1000);
        assertThat(saved.getValorNominalToken()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(saved.getMontoRecaudado()).isEqualByComparingTo(new BigDecimal("25000"));
        assertThat(saved.getGobernanzaComunidad()).isTrue();
    }

    @Test
    void findById_shouldReturnProject() {
        insertProject(1, 1, "Exists", "FINANCIAMIENTO", new BigDecimal("1000"));

        Proyecto found = repository.findById(1L).orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getTitulo()).isEqualTo("Exists");
        assertThat(found.getCreador().getId()).isEqualTo(1L);
    }

    @Test
    void findById_shouldReturnEmptyForMissing() {
        assertThat(repository.findById(999L)).isEmpty();
    }

    @Test
    void findById_shouldReturnEmptyForSoftDeleted() {
        insertProject(1, 1, "Deleted", "CANCELADO", new BigDecimal("500"));
        jdbc.update("UPDATE projects SET deleted_at = NOW() WHERE id = 1");

        assertThat(repository.findById(1L)).isEmpty();
    }

    @Test
    void findAll_shouldReturnPagedResults() {
        insertProject(1, 1, "P1", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "P2", "FINANCIAMIENTO", new BigDecimal("200"));
        insertProject(3, 2, "P3", "PREPARACION", new BigDecimal("300"));

        Page<Proyecto> page = repository.findAll(PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findAll_shouldReturnEmptyPage() {
        Page<Proyecto> page = repository.findAll(PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    void findAll_shouldExcludeSoftDeleted() {
        insertProject(1, 1, "Active", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "Deleted", "CANCELADO", new BigDecimal("200"));
        jdbc.update("UPDATE projects SET deleted_at = NOW() WHERE id = 2");

        Page<Proyecto> page = repository.findAll(PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void findByCreadorId_shouldReturnUserProjects() {
        insertProject(1, 1, "U1P1", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "U1P2", "FINANCIAMIENTO", new BigDecimal("200"));
        insertProject(3, 2, "U2P1", "FINANCIAMIENTO", new BigDecimal("300"));

        Page<Proyecto> page = repository.findByCreadorId(1L, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findByCreadorId_shouldReturnEmptyForMissing() {
        Page<Proyecto> page = repository.findByCreadorId(999L, PageRequest.of(0, 10));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void findByFilters_shouldFilterByEstado() {
        insertProject(1, 1, "Financing", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "Preparing", "PREPARACION", new BigDecimal("200"));

        Page<Proyecto> page = repository.findByFilters(List.of("FINANCIAMIENTO"), null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitulo()).isEqualTo("Financing");
    }

    @Test
    void findByFilters_shouldFilterBySearch() {
        insertProject(1, 1, "Blockchain Project", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "AI Platform", "FINANCIAMIENTO", new BigDecimal("200"));

        Page<Proyecto> page = repository.findByFilters(null, "blockchain", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitulo()).isEqualTo("Blockchain Project");
    }

    @Test
    void findByFilters_shouldCombineEstadoAndSearch() {
        insertProject(1, 1, "Blockchain A", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "Blockchain B", "PREPARACION", new BigDecimal("200"));
        insertProject(3, 1, "AI System", "FINANCIAMIENTO", new BigDecimal("300"));

        Page<Proyecto> page = repository.findByFilters(List.of("FINANCIAMIENTO"), "blockchain", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getTitulo()).isEqualTo("Blockchain A");
    }

    @Test
    void findByFilters_shouldReturnAllWhenNoFilters() {
        insertProject(1, 1, "P1", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "P2", "PREPARACION", new BigDecimal("200"));

        Page<Proyecto> page = repository.findByFilters(null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void findProjectsInFinancing_shouldReturnOnlyFinancing() {
        insertProject(1, 1, "Financing", "FINANCIAMIENTO", new BigDecimal("100"));
        insertProject(2, 1, "Preparing", "PREPARACION", new BigDecimal("200"));
        insertProject(3, 1, "Finished", "FINALIZADO", new BigDecimal("300"));

        List<Proyecto> list = repository.findProjectsInFinancing();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitulo()).isEqualTo("Financing");
    }

    private Proyecto createProject(long creadorId, String titulo, String estado, BigDecimal monto) {
        Proyecto p = new Proyecto();
        Usuario u = new Usuario();
        u.setId(creadorId);
        p.setCreador(u);
        p.setTitulo(titulo);
        p.setDescripcion("Descripcion de " + titulo);
        p.setMontoRequerido(monto);
        p.setPlazo(LocalDateTime.now().plusDays(30));
        p.setEstado(estado);
        p.setGobernanzaComunidad(false);
        return p;
    }

    private void insertProject(long id, long creadorId, String titulo, String estado, BigDecimal monto) {
        jdbc.update("""
            INSERT INTO projects (id, creador_id, titulo, descripcion, monto_requerido, plazo, estado, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, NOW(), ?, NOW(), NOW())
            """, id, creadorId, titulo, "Desc " + titulo, monto, estado);
    }
}
