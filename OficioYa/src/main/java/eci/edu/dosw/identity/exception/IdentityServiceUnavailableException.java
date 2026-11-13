package eci.edu.dosw.identity.exception;

/** El servicio de Identity no responde, falló (5xx) o rechazó el token de servicio. */
public class IdentityServiceUnavailableException extends RuntimeException {
    public IdentityServiceUnavailableException(String message) {
        super(message);
    }

    public IdentityServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}