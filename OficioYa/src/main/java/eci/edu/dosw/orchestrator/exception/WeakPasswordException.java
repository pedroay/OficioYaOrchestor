package eci.edu.dosw.orchestrator.exception;

public class WeakPasswordException extends IdentityException {

    public WeakPasswordException() {
        super("La contraseña no cumple la politica de seguridad.");
    }

    public WeakPasswordException(String message) {
        super(message);
    }
}
