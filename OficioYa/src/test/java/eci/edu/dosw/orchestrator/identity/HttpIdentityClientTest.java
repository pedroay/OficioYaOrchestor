package eci.edu.dosw.orchestrator.identity;

import eci.edu.dosw.identity.exception.AccountLockedException;
import eci.edu.dosw.identity.exception.DuplicateUserException;
import eci.edu.dosw.identity.exception.IdentityServiceUnavailableException;
import eci.edu.dosw.identity.exception.InvalidCredentialsException;
import eci.edu.dosw.identity.exception.OtpRateLimitException;
import eci.edu.dosw.identity.exception.UserNotFoundException;
import eci.edu.dosw.identity.exception.WeakPasswordException;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.OtpResult;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.TokenResult;
import eci.edu.dosw.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpIdentityClientTest {

    private static final String BASE = "http://identity.test";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final String ERROR_NEGOCIO = "{\"error\":\"detalle\",\"message\":\"detalle\"}";
    private static final String TOKEN_RECHAZADO =
            "{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Token ausente, invalido o vencido.\"}";

    private MockRestServiceServer server;
    private HttpIdentityClient client;

    @BeforeEach
    void preparar() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.generateServiceToken()).thenReturn("service-token");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpIdentityClient(builder.build(), jwtService);
    }

    private void esperarLogin(org.springframework.test.web.client.ResponseCreator respuesta) {
        server.expect(requestTo(BASE + "/internal/v1/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(respuesta);
    }

    @Test
    void loginEnviaElTokenDeServicioYDevuelveElToken() {
        server.expect(requestTo(BASE + "/internal/v1/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(USER_ID.toString())))
                .andRespond(withSuccess("{\"token\":\"abc\",\"expiresIn\":900}", MediaType.APPLICATION_JSON));

        TokenResult resultado = client.login(USER_ID, "Clave#2026", List.of("CONTRATANTE"));

        assertEquals("abc", resultado.token());
        assertEquals(900L, resultado.expiresIn());
        server.verify();
    }

    @Test
    void loginCon401DeCredencialesLanzaCredencialesInvalidas() {
        esperarLogin(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(InvalidCredentialsException.class, () -> client.login(USER_ID, "x", List.of()));
    }

    @Test
    void siIdentityRechazaElTokenDeServicioSeTrataComoServicioNoDisponible() {
        esperarLogin(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(TOKEN_RECHAZADO));

        assertThrows(IdentityServiceUnavailableException.class, () -> client.login(USER_ID, "x", List.of()));
    }

    @Test
    void loginCon423LanzaCuentaBloqueada() {
        esperarLogin(withStatus(HttpStatus.LOCKED).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(AccountLockedException.class, () -> client.login(USER_ID, "x", List.of()));
    }

    @Test
    void validarContrasenaDebilCon400ConservaElMensaje() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/validate-password"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Contrasena debil\",\"message\":\"Contrasena debil\"}"));

        WeakPasswordException e = assertThrows(WeakPasswordException.class, () -> client.validatePassword("123"));

        assertEquals("Contrasena debil", e.getMessage());
    }

    @Test
    void validarContrasenaCon204NoLanzaError() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/validate-password"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> client.validatePassword("Segura#Clave2026"));
        server.verify();
    }

    @Test
    void crearCredencialCon201NoLanzaError() {
        server.expect(requestTo(BASE + "/internal/v1/credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CREATED));

        assertDoesNotThrow(() -> client.createCredential(USER_ID, "ana@x.com", "Segura#Clave2026"));
        server.verify();
    }

    @Test
    void crearCredencialCon409LanzaDuplicado() {
        server.expect(requestTo(BASE + "/internal/v1/credentials"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(DuplicateUserException.class,
                () -> client.createCredential(USER_ID, "ana@x.com", "Segura#Clave2026"));
    }

    @Test
    void borrarCredencialUsaDeleteYNoLanzaError() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> client.deleteCredential(USER_ID));
        server.verify();
    }

    @Test
    void cambiarContrasenaUsaPutYCon401LanzaCredencialesInvalidas() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID + "/password"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(InvalidCredentialsException.class, () -> client.changePassword(USER_ID, "mala", "Nueva#Clave2027"));
    }

    @Test
    void generarOtpDevuelveElCodigoYLaVigencia() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID + "/otp"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":\"123456\",\"validMinutes\":15}", MediaType.APPLICATION_JSON));

        OtpResult otp = client.generateOtp(USER_ID);

        assertEquals("123456", otp.code());
        assertEquals(15, otp.validMinutes());
    }

    @Test
    void generarOtpCon404LanzaUsuarioNoEncontrado() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID + "/otp"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(UserNotFoundException.class, () -> client.generateOtp(USER_ID));
    }

    @Test
    void generarOtpCon429LanzaLimiteSuperado() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID + "/otp"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body(ERROR_NEGOCIO));

        assertThrows(OtpRateLimitException.class, () -> client.generateOtp(USER_ID));
    }

    @Test
    void restablecerContrasenaCon204NoLanzaError() {
        server.expect(requestTo(BASE + "/internal/v1/credentials/" + USER_ID + "/password/reset"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withNoContent());

        assertDoesNotThrow(() -> client.resetPassword(USER_ID, "123456", "Nueva#Clave2027"));
        server.verify();
    }

    @Test
    void unErrorDelServidorSeTrataComoServicioNoDisponible() {
        esperarLogin(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThrows(IdentityServiceUnavailableException.class, () -> client.login(USER_ID, "x", List.of()));
    }

    @Test
    void unCodigoInesperadoSeTrataComoServicioNoDisponible() {
        esperarLogin(withStatus(HttpStatus.I_AM_A_TEAPOT));

        assertThrows(IdentityServiceUnavailableException.class, () -> client.login(USER_ID, "x", List.of()));
    }

    @Test
    void siNoHayConexionSeTrataComoServicioNoDisponible() {
        esperarLogin(request -> {
            throw new IOException("conexion rechazada");
        });

        assertThrows(IdentityServiceUnavailableException.class, () -> client.login(USER_ID, "x", List.of()));
    }
}