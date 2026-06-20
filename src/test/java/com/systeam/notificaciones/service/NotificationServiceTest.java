package com.systeam.notificaciones.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.systeam.notificaciones.Notification;
import com.systeam.notificaciones.dto.NotificationResponse;
import com.systeam.notificaciones.dto.UnreadCountResponse;
import com.systeam.notificaciones.repository.JdbcNotificationRepository;
import com.systeam.project.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private JdbcNotificationRepository repository;

    @InjectMocks
    private NotificationService service;

    private final Long userId = 1L;
    private final Long otherUserId = 2L;

    @Test
    void markAsRead_cuandoPropioNoLeido_marcaComoLeido() {
        var notification = Notification.builder()
            .id(42L).recipientUserId(userId).readAt(null).build();
        when(repository.findByIdAndUserId(42L, userId))
            .thenReturn(Optional.of(notification));

        service.markAsRead(42L, userId);

        verify(repository).markAsRead(42L, userId);
    }

    @Test
    void markAsRead_cuandoPropioYaLeido_esIdempotente() {
        var notification = Notification.builder()
            .id(42L).recipientUserId(userId)
            .readAt(LocalDateTime.now()).build();
        when(repository.findByIdAndUserId(42L, userId))
            .thenReturn(Optional.of(notification));

        service.markAsRead(42L, userId);

        // markAsRead on repository should NOT be called — already read
        verify(repository).findByIdAndUserId(42L, userId);
        verify(repository, never()).markAsRead(anyLong(), anyLong());
    }

    @Test
    void markAsRead_cuandoAjeno_lanza404() {
        when(repository.findByIdAndUserId(99L, userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(99L, userId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("99");
    }

    @Test
    void countUnread_devuelveConteo() {
        when(repository.countUnread(userId)).thenReturn(7L);

        UnreadCountResponse response = service.countUnread(userId);

        assertThat(response.count()).isEqualTo(7);
    }

    @Test
    void findByUserId_retornaPagina() {
        var notification = Notification.builder()
            .id(1L).recipientUserId(userId).type("TEST")
            .title("Test").message("Msg").payload("{}")
            .createdAt(LocalDateTime.now()).readAt(null)
            .build();
        var pageable = PageRequest.of(0, 20);
        when(repository.findByUserId(userId, pageable))
            .thenReturn(new PageImpl<>(java.util.List.of(notification), pageable, 1));

        Page<NotificationResponse> result = service.findByUserId(userId, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        NotificationResponse dto = result.getContent().get(0);
        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getType()).isEqualTo("TEST");
        assertThat(dto.getTitle()).isEqualTo("Test");
        assertThat(dto.getMessage()).isEqualTo("Msg");
        assertThat(dto.getPayload()).isEqualTo("{}");
        // recipientUserId must NOT be exposed in the DTO
        assertThat(dto).hasNoNullFieldsOrPropertiesExcept("readAt");
    }
}
