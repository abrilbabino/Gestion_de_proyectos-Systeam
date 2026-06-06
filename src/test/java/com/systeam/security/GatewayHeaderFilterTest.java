package com.systeam.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayHeaderFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private GatewayHeaderFilter filter;

    @BeforeEach
    void setUp() {
        filter = new GatewayHeaderFilter();
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("Gateway header path (X-User-Id presente)")
    class GatewayHeaderPath {

        @BeforeEach
        void stubAuthorization() {
            when(request.getHeader("Authorization")).thenReturn(null);
        }

        @Test
        void headersCompletos_creaAutenticacionConRolesYPermisos() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("42");
            when(request.getHeader("X-User-Email")).thenReturn("user@test.com");
            when(request.getHeader("X-User-Roles")).thenReturn("CREATOR,ADMIN");
            when(request.getHeader("X-User-Permissions")).thenReturn("project:create,project:read");

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);
            assertThat(((JwtPrincipal) auth.getPrincipal()).userId()).isEqualTo(42L);
            assertThat(((JwtPrincipal) auth.getPrincipal()).email()).isEqualTo("user@test.com");
            assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_CREATOR", "ROLE_ADMIN", "project:create", "project:read");
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void soloUserId_creaAutenticacionSinRoles() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("1");
            when(request.getHeader("X-User-Email")).thenReturn("dev@test.com");

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(((JwtPrincipal) auth.getPrincipal()).userId()).isEqualTo(1L);
            assertThat(auth.getAuthorities()).isEmpty();
        }

        @Test
        void rolesVacios_noAgregaAuthorities() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("1");
            when(request.getHeader("X-User-Email")).thenReturn("dev@test.com");
            when(request.getHeader("X-User-Roles")).thenReturn("");
            when(request.getHeader("X-User-Permissions")).thenReturn("");

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).isEmpty();
        }

        @Test
        void rolesConEspacios_trimmeaCorrectamente() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("1");
            when(request.getHeader("X-User-Email")).thenReturn("dev@test.com");
            when(request.getHeader("X-User-Roles")).thenReturn(" CREATOR , INVESTOR ");
            when(request.getHeader("X-User-Permissions")).thenReturn(" project:read ");

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_CREATOR", "ROLE_INVESTOR", "project:read");
        }

        @Test
        void emailNull_principalConEmailNull() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("99");

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(((JwtPrincipal) auth.getPrincipal()).email()).isNull();
        }

        @Test
        void noIntentaJwtCuandoHeaderPresente() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn("1");
            when(request.getHeader("X-User-Email")).thenReturn("dev@test.com");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("JWT fallback path (sin X-User-Id)")
    class JwtPath {

        private String createJwt(String payloadJson) {
            String header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\"}".getBytes());
            String payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes());
            String signature = "fake_signature";
            return header + "." + payload + "." + signature;
        }

        @Test
        void tokenValidoConUserId_creaAutenticacion() throws Exception {
            String jwt = createJwt("{\"userId\":123,\"sub\":\"user@test.com\",\"permissions\":[\"project:read\"],\"roles\":[\"INVESTOR\"]}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(((JwtPrincipal) auth.getPrincipal()).userId()).isEqualTo(123L);
            assertThat(((JwtPrincipal) auth.getPrincipal()).email()).isEqualTo("user@test.com");
            assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("project:read", "ROLE_INVESTOR");
        }

        @Test
        void tokenSoloUserId_sinRolesNiPermisos() throws Exception {
            String jwt = createJwt("{\"userId\":456}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNotNull();
            assertThat(((JwtPrincipal) auth.getPrincipal()).userId()).isEqualTo(456L);
            assertThat(auth.getAuthorities()).isEmpty();
        }

        @Test
        void tokenSinUserId_skipsAutenticacion() throws Exception {
            String jwt = createJwt("{\"sub\":\"user@test.com\"}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
        }

        @Test
        void tokenMalformado_solo2Partes_skipsAutenticacion() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer solo.dos");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void tokenConPayloadInvalido_exceptionAtrapada_skipsAutenticacion() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer header.payloadinvalido.signature");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        @Test
        void tokenConUserIdString_classCastException_securityContextVacio() throws Exception {
            String jwt = createJwt("{\"userId\":\"789\"}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth).isNull();
        }

        @Test
        void permisosYRolesConNull_filtradosSinError() throws Exception {
            String jwt = createJwt("{\"userId\":1,\"permissions\":[null,\"project:read\",null],\"roles\":[null,\"INVESTOR\"]}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("project:read", "ROLE_INVESTOR");
        }

        @Test
        void permisosYRolesVacios_noAgregaAuthorities() throws Exception {
            String jwt = createJwt("{\"userId\":1,\"permissions\":[],\"roles\":[]}");
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt);

            filter.doFilterInternal(request, response, filterChain);

            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Sin autenticación (sin headers ni JWT)")
    class NoAuthPath {

        @Test
        void sinHeaders_sinAuthorization_securityContextVacio() throws Exception {
            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(filterChain).doFilter(request, response);
        }

        @Test
        void authorizationSinBearer_securityContextVacio() throws Exception {
            when(request.getHeader("X-User-Id")).thenReturn(null);
            when(request.getHeader("Authorization")).thenReturn("Basic dXNlcjpwYXNz");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }
    }
}
