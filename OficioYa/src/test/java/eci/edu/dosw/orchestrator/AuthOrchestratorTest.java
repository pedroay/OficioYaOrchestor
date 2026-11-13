package eci.edu.dosw.orchestrator;

import eci.edu.dosw.orchestrator.exception.AccountInactiveException;
import eci.edu.dosw.orchestrator.exception.AccountLockedException;
import eci.edu.dosw.orchestrator.exception.DuplicateUserException;
import eci.edu.dosw.orchestrator.exception.ForbiddenRoleException;
import eci.edu.dosw.orchestrator.exception.IdentityServiceUnavailableException;
import eci.edu.dosw.orchestrator.exception.InvalidCredentialsException;
import eci.edu.dosw.orchestrator.exception.InvalidOtpException;
import eci.edu.dosw.orchestrator.exception.OtpRateLimitException;
import eci.edu.dosw.orchestrator.exception.UserNotFoundException;
import eci.edu.dosw.orchestrator.exception.WeakPasswordException;
import eci.edu.dosw.notification.NotificationService;
import eci.edu.dosw.orchestrator.dto.ChangePasswordRequest;
import eci.edu.dosw.orchestrator.dto.ForgotPasswordRequest;
import eci.edu.dosw.orchestrator.dto.LoginRequest;
import eci.edu.dosw.orchestrator.dto.LoginResponse;
import eci.edu.dosw.orchestrator.dto.PhoneDTO;
import eci.edu.dosw.orchestrator.dto.RegisterRequest;
import eci.edu.dosw.orchestrator.dto.ResetPasswordRequest;
import eci.edu.dosw.orchestrator.identity.IdentityClient;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.OtpResult;
import eci.edu.dosw.orchestrator.identity.IdentityDtos.TokenResult;
import eci.edu.dosw.user.NewUser;
import eci.edu.dosw.user.UserDto;
import eci.edu.dosw.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Prueba unitaria pura del orquestador: Users, Notification e Identity son mocks. */
class AuthOrchestratorTest {

    private static final String CORREO = "ana@x.com";
    private static final String CLAVE = "Segura#Clave2026";
    private static final List<String> ROLES = List.of("CONTRATANTE", "TRABAJADOR");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID USER_ID_9 = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final UUID USER_ID_99 = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private UserService userService;
    private NotificationService notificationService;
    private IdentityClient identityClient;
    private AuthOrchestrator orchestrator;

    @BeforeEach
    void preparar() {
        userService = mock(UserService.class);
        notificationService = mock(NotificationService.class);
        identityClient = mock(IdentityClient.class);
        orchestrator = new AuthOrchestrator(userService, notificationService, identityClient);
    }

    private UserDto usuario(boolean activo) {
        return new UserDto(USER_ID, "Ana", CORREO, List.of(new PhoneDTO("+573001234567", true)),
                ROLES, activo ? "ACTIVE" : "INACTIVE", activo);
    }

    private UserDto perfilCreado() {
        return new UserDto(USER_ID_9, "Ana", CORREO,
                List.of(new PhoneDTO("+573001234567", true)), List.of("CONTRATANTE"), "ACTIVE");
    }

    private RegisterRequest solicitudDeRegistro(List<String> roles) {
        return new RegisterRequest("Ana", CORREO, CLAVE,
                List.of(new PhoneDTO("+573001234567", true)), roles);
    }

    // ---------- login ----------

    @Test
    void loginExitosoDevuelveElTokenDeIdentityConIdYRoles() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.login(USER_ID, CLAVE, ROLES)).thenReturn(new TokenResult("token-abc", 900L));

        LoginResponse respuesta = orchestrator.login(new LoginRequest(CORREO, CLAVE));

        assertEquals("token-abc", respuesta.token());
        assertEquals(900L, respuesta.expiresInSeconds());
        assertEquals(USER_ID, respuesta.userId());
        assertEquals(ROLES, respuesta.roles());
    }

    @Test
    void loginConCorreoInexistenteSeRechazaSinLlamarAIdentity() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> orchestrator.login(new LoginRequest(CORREO, CLAVE)));

        verify(identityClient, never()).login(any(), anyString(), any());
    }

    @Test
    void loginConUsuarioInactivoLanzaAccountInactiveException() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(false)));

        assertThrows(AccountInactiveException.class,
                () -> orchestrator.login(new LoginRequest(CORREO, CLAVE)));

        verify(identityClient, never()).login(any(), anyString(), any());
    }

    @Test
    void loginConContrasenaIncorrectaPropagaElError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.login(USER_ID, "mala", ROLES)).thenThrow(new InvalidCredentialsException());

        assertThrows(InvalidCredentialsException.class,
                () -> orchestrator.login(new LoginRequest(CORREO, "mala")));
    }

    @Test
    void loginConCuentaBloqueadaPropagaElBloqueo() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.login(USER_ID, CLAVE, ROLES)).thenThrow(new AccountLockedException());

        assertThrows(AccountLockedException.class,
                () -> orchestrator.login(new LoginRequest(CORREO, CLAVE)));
    }

    @Test
    void loginConIdentityCaidoPropagaElError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.login(USER_ID, CLAVE, ROLES))
                .thenThrow(new IdentityServiceUnavailableException("caido"));

        assertThrows(IdentityServiceUnavailableException.class,
                () -> orchestrator.login(new LoginRequest(CORREO, CLAVE)));
    }

    // ---------- register ----------

    @Test
    void registroExitosoValidaLaClaveLuegoCreaElPerfilYLaCredencial() {
        UserDto creado = perfilCreado();
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());
        when(userService.createProfile(any(NewUser.class))).thenReturn(creado);

        UserDto resultado = orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE")));

        assertEquals(creado, resultado);
        ArgumentCaptor<NewUser> captor = ArgumentCaptor.forClass(NewUser.class);
        verify(userService).createProfile(captor.capture());
        assertEquals("Ana", captor.getValue().name());
        assertEquals(CORREO, captor.getValue().email());
        assertEquals(List.of("CONTRATANTE"), captor.getValue().roles());

        InOrder orden = inOrder(identityClient, userService);
        orden.verify(identityClient).validatePassword(CLAVE);
        orden.verify(userService).createProfile(any(NewUser.class));
        orden.verify(identityClient).createCredential(USER_ID_9, CORREO, CLAVE);
    }

    @Test
    void registroNoPermiteElRolAdministradorOService() {
        assertThrows(ForbiddenRoleException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE", "ADMIN"))));
        assertThrows(ForbiddenRoleException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("SERVICE"))));

        verify(userService, never()).createProfile(any());
        verify(identityClient, never()).validatePassword(any());
    }

    @Test
    void registroConContrasenaDebilNoCreaNada() {
        doThrow(new WeakPasswordException()).when(identityClient).validatePassword(CLAVE);

        assertThrows(WeakPasswordException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE"))));

        verify(userService, never()).createProfile(any());
        verify(identityClient, never()).createCredential(any(), any(), any());
    }

    @Test
    void registroConCorreoRepetidoNoCreaNada() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));

        assertThrows(DuplicateUserException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE"))));

        verify(userService, never()).createProfile(any());
        verify(identityClient, never()).createCredential(any(), any(), any());
    }

    @Test
    void siFallaLaCredencialSeBorraElPerfilCreado() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());
        when(userService.createProfile(any(NewUser.class))).thenReturn(perfilCreado());
        RuntimeException fallo = new RuntimeException("fallo");
        doThrow(fallo).when(identityClient).createCredential(USER_ID_9, CORREO, CLAVE);

        RuntimeException lanzada = assertThrows(RuntimeException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE"))));

        assertEquals(fallo, lanzada);
        verify(userService).deleteProfile(USER_ID_9);
    }

    @Test
    void siIdentityEstaCaidoAlRegistrarSeBorraElPerfilYSePropagaElError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());
        when(userService.createProfile(any(NewUser.class))).thenReturn(perfilCreado());
        doThrow(new IdentityServiceUnavailableException("caido"))
                .when(identityClient).createCredential(USER_ID_9, CORREO, CLAVE);

        assertThrows(IdentityServiceUnavailableException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE"))));

        verify(userService).deleteProfile(USER_ID_9);
    }

    @Test
    void siTambienFallaElBorradoSeConservaElErrorOriginal() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());
        when(userService.createProfile(any(NewUser.class))).thenReturn(perfilCreado());
        doThrow(new RuntimeException("identity caido")).when(identityClient).createCredential(USER_ID_9, CORREO, CLAVE);
        doThrow(new IllegalStateException("no se pudo borrar")).when(userService).deleteProfile(USER_ID_9);

        RuntimeException lanzada = assertThrows(RuntimeException.class,
                () -> orchestrator.register(solicitudDeRegistro(List.of("CONTRATANTE"))));

        assertEquals("identity caido", lanzada.getMessage());
        assertEquals(1, lanzada.getSuppressed().length);
    }

    // ---------- changePassword ----------

    private static final String ACTUAL = "Actual#Clave2026";
    private static final String NUEVA = "Nueva#Clave2027";

    @Test
    void cambiaLaContrasenaYAvisaPorCorreo() {
        when(userService.findById(USER_ID)).thenReturn(Optional.of(usuario(true)));

        orchestrator.changePassword(USER_ID, new ChangePasswordRequest(ACTUAL, NUEVA));

        verify(identityClient).changePassword(USER_ID, ACTUAL, NUEVA);
        verify(notificationService).sendPasswordChangedNotice(CORREO);
    }

    @Test
    void conLaContrasenaActualIncorrectaNoSeAvisa() {
        when(userService.findById(USER_ID)).thenReturn(Optional.of(usuario(true)));
        doThrow(new InvalidCredentialsException()).when(identityClient).changePassword(USER_ID, "mala", NUEVA);

        assertThrows(InvalidCredentialsException.class,
                () -> orchestrator.changePassword(USER_ID, new ChangePasswordRequest("mala", NUEVA)));

        verify(notificationService, never()).sendPasswordChangedNotice(any());
    }

    @Test
    void cambioDeUnUsuarioInexistenteLanzaNoEncontrado() {
        when(userService.findById(USER_ID_99)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> orchestrator.changePassword(USER_ID_99, new ChangePasswordRequest(ACTUAL, NUEVA)));

        verify(identityClient, never()).changePassword(any(), any(), any());
    }

    @Test
    void siFallaElAvisoElCambioDeContrasenaSeMantiene() {
        when(userService.findById(USER_ID)).thenReturn(Optional.of(usuario(true)));
        doThrow(new RuntimeException("correo caido")).when(notificationService).sendPasswordChangedNotice(CORREO);

        assertDoesNotThrow(() -> orchestrator.changePassword(USER_ID, new ChangePasswordRequest(ACTUAL, NUEVA)));

        verify(identityClient).changePassword(USER_ID, ACTUAL, NUEVA);
    }

    // ---------- forgotPassword ----------

    @Test
    void olvidoPideElCodigoAIdentityYLoEnvia() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.generateOtp(USER_ID)).thenReturn(new OtpResult("123456", 15));

        orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO));

        verify(notificationService).sendPasswordResetCode(CORREO, "123456", 15);
    }

    @Test
    void olvidoConCorreoInexistenteNoHaceNadaNiLanzaError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO)));

        verify(identityClient, never()).generateOtp(any());
        verify(notificationService, never()).sendPasswordResetCode(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void olvidoConUsuarioInactivoNoHaceNadaNiLanzaError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(false)));

        assertDoesNotThrow(() -> orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO)));

        verify(identityClient, never()).generateOtp(any());
    }

    @Test
    void alSuperarElLimiteDeSolicitudesNoHayErrorNiSeEnviaCodigo() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.generateOtp(USER_ID)).thenThrow(new OtpRateLimitException());

        assertDoesNotThrow(() -> orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO)));

        verify(notificationService, never()).sendPasswordResetCode(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void olvidoConPerfilSinCredencialNoLanzaError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.generateOtp(USER_ID)).thenThrow(new UserNotFoundException());

        assertDoesNotThrow(() -> orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO)));

        verify(notificationService, never()).sendPasswordResetCode(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void olvidoConIdentityCaidoPropagaElError() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        when(identityClient.generateOtp(USER_ID)).thenThrow(new IdentityServiceUnavailableException("caido"));

        assertThrows(IdentityServiceUnavailableException.class,
                () -> orchestrator.forgotPassword(new ForgotPasswordRequest(CORREO)));
    }

    // ---------- resetPassword ----------

    @Test
    void restablecerPideAIdentityElCambioYAvisaPorCorreo() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));

        orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", NUEVA));

        verify(identityClient).resetPassword(USER_ID, "123456", NUEVA);
        verify(notificationService).sendPasswordChangedNotice(CORREO);
    }

    @Test
    void conUnaContrasenaNuevaInvalidaSePropagaYNoSeAvisa() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        doThrow(new WeakPasswordException()).when(identityClient).resetPassword(USER_ID, "123456", "debil");

        assertThrows(WeakPasswordException.class,
                () -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", "debil")));

        verify(notificationService, never()).sendPasswordChangedNotice(any());
    }

    @Test
    void conUnCodigoIncorrectoSePropagaYNoSeAvisa() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        doThrow(new InvalidOtpException()).when(identityClient).resetPassword(USER_ID, "000000", NUEVA);

        assertThrows(InvalidOtpException.class,
                () -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "000000", NUEVA)));

        verify(notificationService, never()).sendPasswordChangedNotice(any());
    }

    @Test
    void restablecerConCorreoInexistenteDaCodigoInvalido() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.empty());

        assertThrows(InvalidOtpException.class,
                () -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", NUEVA)));

        verify(identityClient, never()).resetPassword(any(), any(), any());
    }

    @Test
    void restablecerConUsuarioInactivoDaCodigoInvalido() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(false)));

        assertThrows(InvalidOtpException.class,
                () -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", NUEVA)));

        verify(identityClient, never()).resetPassword(any(), any(), any());
    }

    @Test
    void restablecerConPerfilSinCredencialDaCodigoInvalido() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        doThrow(new UserNotFoundException()).when(identityClient).resetPassword(USER_ID, "123456", NUEVA);

        assertThrows(InvalidOtpException.class,
                () -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", NUEVA)));
    }

    @Test
    void siFallaElAvisoElRestablecimientoSeMantiene() {
        when(userService.findByEmail(CORREO)).thenReturn(Optional.of(usuario(true)));
        doThrow(new RuntimeException("correo caido")).when(notificationService).sendPasswordChangedNotice(CORREO);

        assertDoesNotThrow(() -> orchestrator.resetPassword(new ResetPasswordRequest(CORREO, "123456", NUEVA)));

        verify(identityClient).resetPassword(USER_ID, "123456", NUEVA);
    }
}