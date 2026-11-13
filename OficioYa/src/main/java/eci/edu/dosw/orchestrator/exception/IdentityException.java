package eci.edu.dosw.orchestrator.exception;

/** Base de los errores de negocio del modulo Identity. */
public abstract class IdentityException extends RuntimeException {

    protected IdentityException(String message) {
        super(message);
    }
}
