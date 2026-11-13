package eci.edu.dosw.orchestrator.identity;

import java.util.List;
import java.util.UUID;

/** Cuerpos de petición y respuesta de la API interna de Identity (/internal/v1). */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    public record ValidatePasswordBody(String password) {
    }

    public record CreateCredentialBody(UUID userId, String email, String password) {
    }

    public record LoginBody(UUID userId, String password, List<String> roles) {
    }

    public record ChangePasswordBody(String currentPassword, String newPassword) {
    }

    public record ResetPasswordBody(String code, String newPassword) {
    }

    public record TokenResult(String token, long expiresIn) {
    }

    public record OtpResult(String code, int validMinutes) {
    }
}