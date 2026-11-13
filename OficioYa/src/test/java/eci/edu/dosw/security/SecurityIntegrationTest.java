package eci.edu.dosw.security;

import eci.edu.dosw.orchestrator.AuthOrchestrator;
import eci.edu.dosw.orchestrator.dto.ChangePasswordRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seguridad del gateway: valida los JWT que emite Identity (firma, vigencia, roles).
 * Los tokens de usuario se firman aquí con el secreto de pruebas, igual que lo haría Identity.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtService jwtService;
    @Value("${jwt.secret}")
    private String secret;

    @MockitoBean
    private AuthOrchestrator authOrchestrator;

    private static String emitir(String secretBase64, long vigenciaMs, String subject, List<String> roles) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("roles", roles)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plusMillis(vigenciaMs)))
                .signWith(key)
                .compact();
    }

    private String token(String... roles) {
        return emitir(secret, 900_000L, USER_ID.toString(), List.of(roles));
    }

    private ResultActions get(String ruta, String token) throws Exception {
        MockHttpServletRequestBuilder req = MockMvcRequestBuilders.get(ruta);
        if (token != null) {
            req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    // --- token válido ---
    @Test
    void unTokenValidoPasaYElControladorVeElIdDelToken() throws Exception {
        get("/api/probe", token("CONTRATANTE")).andExpect(status().isOk())
                .andExpect(content().string(USER_ID.toString()));
    }

    // --- sin token / inválido ---
    @Test
    void sinTokenEnRutaProtegidaDevuelve401() throws Exception {
        get("/api/probe", null).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void headerQueNoEsBearerDevuelve401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/probe").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenMalformadoDevuelve401() throws Exception {
        get("/api/probe", "esto.no.es.un.jwt").andExpect(status().isUnauthorized());
    }

    @Test
    void tokenVacioDevuelve401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/probe").header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());
    }

    // --- vencido ---
    @Test
    void tokenVencidoDevuelve401() throws Exception {
        String vencido = emitir(secret, -1000L, USER_ID.toString(), List.of("CONTRATANTE"));
        get("/api/probe", vencido).andExpect(status().isUnauthorized());
    }

    // --- manipulado ---
    @Test
    void tokenFirmadoConOtraClaveDevuelve401() throws Exception {
        String otraClave = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().getEncoded());
        String ajeno = emitir(otraClave, 900_000L, USER_ID.toString(), List.of("ADMIN"));
        get("/api/admin/probe", ajeno).andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarElRolDentroDelTokenInvalidaLaFirma() throws Exception {
        String[] partes = token("CONTRATANTE").split("\\.");
        String original = new String(Base64.getUrlDecoder().decode(partes[1]), UTF_8);
        String alterado = original.replace("\"CONTRATANTE\"", "\"ADMIN\"");
        assertNotEquals(original, alterado);
        String falso = partes[0] + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(alterado.getBytes(UTF_8))
                + "." + partes[2];
        get("/api/admin/probe", falso).andExpect(status().isUnauthorized());
    }

    // --- el token de servicio es interno ---
    @Test
    void elTokenDeServicioNoAutenticaTraficoExterno() throws Exception {
        get("/api/probe", jwtService.generateServiceToken()).andExpect(status().isUnauthorized());
        get("/api/admin/probe", jwtService.generateServiceToken()).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenDeUsuarioConRolServicioNoGanaPrivilegios() throws Exception {
        get("/api/admin/probe", token("SERVICE")).andExpect(status().isForbidden());
    }

    // --- roles (RF-07) ---
    @Test
    void elRolCorrectoAccedeALaRutaDeRol() throws Exception {
        get("/api/admin/probe", token("ADMIN")).andExpect(status().isOk());
        get("/api/worker/probe", token("TRABAJADOR")).andExpect(status().isOk());
        get("/api/v1/admin/probe", token("ADMIN")).andExpect(status().isOk());
        get("/api/v1/worker/probe", token("TRABAJADOR")).andExpect(status().isOk());
    }

    @Test
    void unRolInsuficienteRecibe403() throws Exception {
        get("/api/admin/probe", token("CONTRATANTE")).andExpect(status().isForbidden());
        get("/api/v1/admin/probe", token("CONTRATANTE")).andExpect(status().isForbidden());
        get("/api/v1/worker/probe", token("CONTRATANTE")).andExpect(status().isForbidden());
    }

    @Test
    void sinRutaDeRolYSinTokenDevuelve401() throws Exception {
        get("/api/admin/probe", null).andExpect(status().isUnauthorized());
    }

    @Test
    void unTokenSinRolesAutenticaPeroNoPasaLasRutasDeRol() throws Exception {
        String sinRol = token();
        get("/api/probe", sinRol).andExpect(status().isOk());
        get("/api/admin/probe", sinRol).andExpect(status().isForbidden());
        get("/api/worker/probe", sinRol).andExpect(status().isForbidden());
    }

    @Test
    void rolesDesconocidosEnElTokenSeIgnoran() throws Exception {
        get("/api/admin/probe", token("SUPERUSER")).andExpect(status().isForbidden());
    }

    // --- rutas públicas ---
    @Test
    void lasRutasPublicasNoExigenToken() throws Exception {
        for (String ruta : List.of("/api/v1/auth/login", "/api/v1/auth/register",
                "/api/v1/auth/forgot", "/api/v1/auth/reset")) {
            mvc.perform(MockMvcRequestBuilders.post(ruta))
                    .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
        }
    }

    @Test
    void lasRutasViejasSinVersionYaNoSonPublicas() throws Exception {
        for (String ruta : List.of("/api/auth/login", "/api/auth/register",
                "/api/auth/forgot-password", "/api/auth/reset-password")) {
            mvc.perform(MockMvcRequestBuilders.post(ruta))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void unTokenVencidoEnUnaRutaPublicaNoLaBloquea() throws Exception {
        String vencido = emitir(secret, -1000L, USER_ID.toString(), List.of("CONTRATANTE"));
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login")
                .header("Authorization", "Bearer " + vencido))
                .andExpect(result -> assertNotEquals(401, result.getResponse().getStatus()));
    }

    @Test
    void unaRutaNoListadaExigeToken() throws Exception {
        get("/api/cualquier-cosa", null).andExpect(status().isUnauthorized());
    }

    // --- cambio de contraseña: el id sale del token, no de la URL ---
    @Test
    void cambiarContrasenaSinTokenDevuelve401() throws Exception {
        mvc.perform(MockMvcRequestBuilders.put("/api/v1/auth/change")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cambiarContrasenaUsaElIdDelTokenYNoElDeLaUrl() throws Exception {
        UUID victima = UUID.randomUUID();

        mvc.perform(MockMvcRequestBuilders.put("/api/v1/auth/change")
                .param("userId", victima.toString())
                .header("Authorization", "Bearer " + token("CONTRATANTE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Actual#Clave2026\",\"newPassword\":\"Nueva#Clave2026X\"}"))
                .andExpect(status().isOk());

        verify(authOrchestrator).changePassword(eq(USER_ID), any(ChangePasswordRequest.class));
        verify(authOrchestrator, never()).changePassword(eq(victima), any(ChangePasswordRequest.class));
    }
}