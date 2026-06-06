package com.systeam.project.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Nested
    @DisplayName("handleAccessDenied")
    class HandleAccessDenied {

        @Test
        void retorna403() {
            ResponseEntity<Map<String, String>> res = handler.handleAccessDenied(new AccessDeniedException("denied"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(res.getBody()).containsEntry("error", "Acceso denegado");
        }
    }

    @Nested
    @DisplayName("handleOracleBillingNotFound")
    class HandleOracleBillingNotFound {

        @Test
        void retorna422() {
            ResponseEntity<Map<String, String>> res = handler.handleOracleBillingNotFound(
                new OracleBillingNotFoundException("Oracle no encontrado"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            assertThat(res.getBody()).containsEntry("error", "Oracle no encontrado");
        }
    }

    @Nested
    @DisplayName("handleRuntimeException")
    class HandleRuntimeException {

        @Test
        void retorna400() {
            ResponseEntity<Map<String, String>> res = handler.handleRuntimeException(
                new RuntimeException("Algo salio mal"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).containsEntry("error", "Algo salio mal");
        }
    }

    @Nested
    @DisplayName("handleValidationException")
    class HandleValidationException {

        @Test
        void retorna400ConMapaDeErrores() {
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("obj", "titulo", "El titulo es obligatorio"),
                new FieldError("obj", "monto", "Debe ser mayor a 0")
            ));
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<Map<String, String>> res = handler.handleValidationException(ex);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).containsEntry("titulo", "El titulo es obligatorio");
            assertThat(res.getBody()).containsEntry("monto", "Debe ser mayor a 0");
        }

        @Test
        void sinErrores_retornaMapaVacio() {
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.getFieldErrors()).thenReturn(List.of());
            MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

            ResponseEntity<Map<String, String>> res = handler.handleValidationException(ex);

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(res.getBody()).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleNotFound")
    class HandleNotFound {

        @Test
        void retorna404() {
            ResponseEntity<Map<String, String>> res = handler.handleNotFound(
                new ResourceNotFoundException("Proyecto no encontrado"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(res.getBody()).containsEntry("error", "Proyecto no encontrado");
        }
    }

    @Nested
    @DisplayName("handleConflict")
    class HandleConflict {

        @Test
        void retorna409() {
            ResponseEntity<Map<String, String>> res = handler.handleConflict(
                new ConflictException("El proyecto ya existe"));

            assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(res.getBody()).containsEntry("error", "El proyecto ya existe");
        }
    }
}
