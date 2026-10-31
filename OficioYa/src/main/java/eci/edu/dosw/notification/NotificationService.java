package eci.edu.dosw.notification;

/** Contrato del modulo Notification (de otro equipo). */
public interface NotificationService {

    /** Envia el codigo OTP de recuperacion de contrasena. */
    void sendPasswordResetCode(String email, String code, int validMinutes);

    /** Avisar que la contrasena fue cambiada. */
    void sendPasswordChangedNotice(String email);
}