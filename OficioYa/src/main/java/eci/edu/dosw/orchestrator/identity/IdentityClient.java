package eci.edu.dosw.orchestrator.identity;

import eci.edu.dosw.orchestrator.identity.IdentityDtos.OtpResult;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.TokenResult;

import java.util.List;
import java.util.UUID;

/**
 * Operaciones que el orquestador necesita del servicio de Identity.
 * Identity solo autentica: no conoce perfiles de usuario ni envía notificaciones.
 */
public interface IdentityClient {

    void validatePassword(String password);

    void createCredential(UUID userId, String email, String password);

    void deleteCredential(UUID userId);

    TokenResult login(UUID userId, String password, List<String> roles);

    void changePassword(UUID userId, String currentPassword, String newPassword);

    OtpResult generateOtp(UUID userId);

    void resetPassword(UUID userId, String code, String newPassword);
}