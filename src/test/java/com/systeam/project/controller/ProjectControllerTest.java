package com.systeam.project.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systeam.project.dto.CreateProjectRequest;
import com.systeam.project.dto.UpdateProjectRequest;
import com.systeam.project.service.BoostService;
import com.systeam.project.service.ProjectService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProjectController.class)
@Import(ProjectControllerTest.TestSecurityConfig.class)
class ProjectControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private BoostService boostService;

    @Nested
    @DisplayName("POST /api/projects")
    class CreateProject {

        @Test
        @WithMockUser(authorities = "project:read")
        void sinPermisoCreate_retorna403() throws Exception {
            CreateProjectRequest request = new CreateProjectRequest();
            request.setTitulo("Test");
            request.setDescripcion("Desc");
            request.setMontoRequerido(new BigDecimal("100"));
            request.setPlazo(LocalDateTime.now().plusDays(30));
            request.setCupoMaximoTokens(10);
            request.setValorNominalToken(new BigDecimal("10"));
            request.setSimbolo("TST");

            mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser
        void sinPermiso_retorna403() throws Exception {
            CreateProjectRequest request = new CreateProjectRequest();
            request.setTitulo("Test");
            request.setDescripcion("Desc");
            request.setMontoRequerido(new BigDecimal("100"));
            request.setPlazo(LocalDateTime.now().plusDays(30));
            request.setCupoMaximoTokens(10);
            request.setValorNominalToken(new BigDecimal("10"));
            request.setSimbolo("TST");

            mockMvc.perform(post("/api/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PUT /api/projects/{id}")
    class UpdateProject {

        @Test
        @WithMockUser(authorities = "project:create")
        void sinPermisoUpdate_retorna403() throws Exception {
            UpdateProjectRequest request = new UpdateProjectRequest();
            request.setTitulo("Nuevo");

            mockMvc.perform(put("/api/projects/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("PATCH /api/projects/{id}/status")
    class UpdateProjectStatus {

        @Test
        @WithMockUser(authorities = "project:read")
        void sinPermisoUpdate_retorna403() throws Exception {
            mockMvc.perform(patch("/api/projects/1/status")
                    .param("status", "FINANCIAMIENTO"))
                .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("POST /api/projects/evaluate-states")
    class EvaluateStates {

        @Test
        @WithMockUser
        void sinRolAdmin_retorna403() throws Exception {
            mockMvc.perform(post("/api/projects/evaluate-states"))
                .andExpect(status().isForbidden());
        }
    }
}
