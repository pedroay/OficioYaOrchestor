package eci.edu.dosw.orchestrator.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eci.edu.dosw.identity.exception.AccountLockedException;
import eci.edu.dosw.identity.exception.DuplicateUserException;
import eci.edu.dosw.identity.exception.IdentityException;
import eci.edu.dosw.identity.exception.IdentityServiceUnavailableException;
import eci.edu.dosw.identity.exception.InvalidCredentialsException;
import eci.edu.dosw.identity.exception.OtpRateLimitException;
import eci.edu.dosw.identity.exception.UserNotFoundException;
import eci.edu.dosw.identity.exception.WeakPasswordException;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.ChangePasswordBody;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.CreateCredentialBody;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.LoginBody;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.OtpResult;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.ResetPasswordBody;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.TokenResult;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.ValidatePasswordBody;
import eci.edu.dosw.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cliente HTTP de la API interna de Identity. Se autentica con un token de servicio
 * de vida corta y traduce las respuestas de error a las excepciones del orquestador.
 */
@Service
public class HttpIdentityClient implements IdentityClient {

    private static final Logger log = LoggerFactory.getLogger(HttpIdentityClient.class);

    private final RestClient restClient;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public HttpIdentityClient(
            @Value("${identity.service.url:http://localhost:8085}") String identityServiceUrl,
            JwtService jwtService) {
        this(crearRestClient(identityServiceUrl), jwtService);
    }

    public HttpIdentityClient(RestClient restClient, JwtService jwtService) {
        this.restClient = restClient;
        this.jwtService = jwtService;
    }

    private static RestClient crearRestClient(String url) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    @Override
    public void validatePassword(String password) {
        ejecutar(() -> restClient.post()
                .uri("/internal/v1/credentials/validate-password")
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ValidatePasswordBody(password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .toBodilessEntity());
    }

    @Override
    public void createCredential(UUID userId, String email, String password) {
        ejecutar(() -> restClient.post()
                .uri("/internal/v1/credentials")
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCredentialBody(userId, email, password))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .toBodilessEntity());
    }

    @Override
    public void deleteCredential(UUID userId) {
        ejecutar(() -> restClient.delete()
                .uri("/internal/v1/credentials/{userId}", userId)
                .headers(this::autenticar)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .toBodilessEntity());
    }

    @Override
    public TokenResult login(UUID userId, String password, List<String> roles) {
        return ejecutar(() -> restClient.post()
                .uri("/internal/v1/auth/login")
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(new LoginBody(userId, password, roles))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .body(TokenResult.class));
    }

    @Override
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        ejecutar(() -> restClient.put()
                .uri("/internal/v1/credentials/{userId}/password", userId)
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ChangePasswordBody(currentPassword, newPassword))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .toBodilessEntity());
    }

    @Override
    public OtpResult generateOtp(UUID userId) {
        return ejecutar(() -> restClient.post()
                .uri("/internal/v1/credentials/{userId}/otp", userId)
                .headers(this::autenticar)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .body(OtpResult.class));
    }

    @Override
    public void resetPassword(UUID userId, String code, String newPassword) {
        ejecutar(() -> restClient.post()
                .uri("/internal/v1/credentials/{userId}/password/reset", userId)
                .headers(this::autenticar)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ResetPasswordBody(code, newPassword))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::rechazar)
                .toBodilessEntity());
    }

    private void autenticar(HttpHeaders headers) {
        headers.setBearerAuth(jwtService.generateServiceToken());
    }

    private void rechazar(HttpRequest request, ClientHttpResponse response) throws IOException {
        throw traducir(response.getStatusCode().value(), leerCuerpo(response));
    }

    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (IdentityException | IdentityServiceUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Error al comunicarse con Identity: {}", e.getMessage());
            throw new IdentityServiceUnavailableException("El servicio de identidad no está disponible", e);
        }
    }

    /**
     * Identity responde 401/403 con un cuerpo que incluye "status" cuando rechaza el token de
     * servicio (problema de configuración), y sin "status" cuando son credenciales inválidas.
     * Un 400 puede ser contraseña débil o código OTP inválido: ambos se devuelven como 400.
     */
    private RuntimeException traducir(int status, JsonNode body) {
        String message = body != null && body.hasNonNull("message") ? body.get("message").asText() : null;
        boolean tokenRechazado = body != null && body.has("status");
        if (status >= 500 || tokenRechazado) {
            log.error("Identity respondió {} (tokenRechazado={})", status, tokenRechazado);
            return new IdentityServiceUnavailableException("El servicio de identidad no está disponible");
        }
        return switch (status) {
            case 400 -> message != null ? new WeakPasswordException(message) : new WeakPasswordException();
            case 401 -> new InvalidCredentialsException();
            case 404 -> new UserNotFoundException();
            case 409 -> new DuplicateUserException();
            case 423 -> new AccountLockedException();
            case 429 -> new OtpRateLimitException();
            default -> new IdentityServiceUnavailableException(
                    "Respuesta inesperada del servicio de identidad (" + status + ")");
        };
    }

    private JsonNode leerCuerpo(ClientHttpResponse response) {
        try {
            byte[] bytes = response.getBody().readAllBytes();
            return bytes.length == 0 ? null : objectMapper.readTree(bytes);
        } catch (Exception e) {
            return null;
        }
    }
}