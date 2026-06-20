package com.systeam.notificaciones.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.systeam.notificaciones.dto.NotificationResponse;
import com.systeam.notificaciones.dto.UnreadCountResponse;
import com.systeam.notificaciones.service.NotificationService;
import com.systeam.project.exception.ResourceNotFoundException;
import com.systeam.security.JwtPrincipal;

@WebMvcTest(NotificationController.class)
@Import(NotificationControllerTest.TestSecurityConfig.class)
class NotificationControllerTest {

    @EnableMethodSecurity
    static class TestSecurityConfig {}

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @BeforeEach
    void setup() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    new JwtPrincipal(1L, "test@test.com"),
                    auth.getCredentials(),
                    auth.getAuthorities()
                )
            );
        }
    }

    @Test
    void getNotifications_sinAuth_retorna401() throws Exception {
        // clear security context
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void getNotifications_conAuth_retorna200() throws Exception {
        NotificationResponse dto = NotificationResponse.builder()
            .id(1L).type("TEST").title("T").message("M")
            .payload("{}").build();
        Page<NotificationResponse> page = new PageImpl<>(
            java.util.List.of(dto), PageRequest.of(0, 20), 1);
        when(notificationService.findByUserId(eq(1L), any()))
            .thenReturn(page);

        mockMvc.perform(get("/api/notifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    void markAsRead_propio_retorna200() throws Exception {
        mockMvc.perform(patch("/api/notifications/42/read")
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void markAsRead_ajenoOInexistente_retorna404() throws Exception {
        doThrow(new ResourceNotFoundException("Notification not found with id: 99"))
            .when(notificationService).markAsRead(99L, 1L);

        mockMvc.perform(patch("/api/notifications/99/read")
                .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void unreadCount_conAuth_retorna200() throws Exception {
        when(notificationService.countUnread(1L))
            .thenReturn(new UnreadCountResponse(7));

        mockMvc.perform(get("/api/notifications/unread-count"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(7));
    }
}
