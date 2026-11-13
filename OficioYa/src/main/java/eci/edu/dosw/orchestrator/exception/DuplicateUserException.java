package eci.edu.dosw.orchestrator.exception;

public class DuplicateUserException extends IdentityException {

    public DuplicateUserException() {
        super("Ya existe un usuario registrado con ese correo.");
    }

    public DuplicateUserException(String message) {
        super(message);
    }
}
