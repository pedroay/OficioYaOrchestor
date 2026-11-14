#  Documento de Logs y Trazabilidad del Proyecto — OficioYa (RNF-03)

**Squad 1:** Samuel A, Pedro A, Javier C, Juanita R.  
**Módulos cubiertos:** `OficioYaOrchestor` (API Gateway & Saga Orchestrator) · `oficioya-identity` (Identity Domain) · `oficioYa-images` (Image Service)  
**Estándar implementado:** SLF4J + Logback (Spring Boot 4.1.2)

---

## 1. Estándar y Arquitectura de Logging

En cumplimiento del **Requerimiento No Funcional 3 (RNF-03)** (*"El servicio debe generar logs estructurados para trazabilidad y debugging"*), el ecosistema backend de **OficioYa** utiliza **SLF4J (`org.slf4j.Logger`)** sobre **Logback** con el patrón estructurado de Spring Boot:

```text
YYYY-MM-DD HH:mm:ss.SSS [thread] LEVEL logger_class - mensaje estructurado (clave=valor)
```

### 1.1 Reglas de Seguridad en los Logs (Prevención de Fuga de Datos)
1. **Nunca se registran contraseñas en texto plano ni hashes BCrypt** en ningún nivel de log (`INFO`, `WARN`, `ERROR`, `DEBUG`).
2. **Nunca se registran tokens JWT completos** en los archivos de log; únicamente se registra el `userId` (UUID) o el estado de validación de la firma.
3. **Trazabilidad por `userId` (UUID):** Todas las operaciones críticas de negocio (registro, login, cambio de contraseña, generación de OTP y compensación Saga) incluyen `userId=<UUID>` para correlacionar eventos entre el **Orquestador**, **Identity** y **Users**.

---

## 2. Matriz de Niveles de Log Implementados

| Nivel | Propósito en OficioYa | Componentes que lo emiten | Ejemplo de Evento |
| :---: | :--- | :--- | :--- |
| **`INFO`** | Confirmación de flujos de negocio completados correctamente y arranque de servicios. | `AuthOrchestrator`, `InternalIdentityService`, `ImageService`, `App` | Inicio de sesión exitoso, registro completado, cambio/restablecimiento de contraseña, envío de OTP. |
| **`WARN`** | Eventos de seguridad controlados, intentos fallidos de acceso, bloqueos de cuenta o ejecución de rollbacks compensatorios (Saga). | `AuthOrchestrator`, `GlobalExceptionHandler`, `JwtAuthenticationFilter` | Credenciales inválidas (`401`), cuenta inactiva/bloqueada (`403`/`423`), token JWT ausente/adulterado, compensación Saga (`deleteProfile`). |
| **`ERROR`** | Fallos críticos de infraestructura, caída de microservicios aguas abajo o fallo en una compensación distribuida. | `GlobalExceptionHandler`, `AuthOrchestrator`, `HttpIdentityClient`, `HttpUserService` | Microservicio `Users` o `Identity` no disponible (`503`), fallo al ejecutar rollback de perfil huérfano. |

---

## 3. Evidencia de Logs por Escenario de Negocio y Seguridad

### Escenario 1: Arranque del Orquestador y Conexión de Filtros de Seguridad (`INFO`)
```log
2026-11-13 22:15:04.112 [main] INFO  eci.edu.dosw.App - Starting App v0.0.1-SNAPSHOT using Java 21.0.6 with PID 18420
2026-11-13 22:15:04.118 [main] INFO  eci.edu.dosw.App - The following 1 profile is active: "default"
2026-11-13 22:15:06.430 [main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat initialized with port 8080 (http)
2026-11-13 22:15:07.215 [main] INFO  o.s.s.web.DefaultSecurityFilterChain - Will secure any request with [DisableEncodeUrlFilter, WebAsyncManagerIntegrationFilter, SecurityContextHolderFilter, HeaderWriterFilter, CorsFilter, LogoutFilter, JwtAuthenticationFilter, RequestCacheAwareFilter, SecurityContextHolderAwareRequestFilter, AnonymousAuthenticationFilter, SessionManagementFilter, ExceptionTranslationFilter, AuthorizationFilter]
2026-11-13 22:15:07.890 [main] INFO  eci.edu.dosw.App - Started App in 4.125 seconds (process running for 4.610)
```

---

### Escenario 2: Registro de Usuario Exitoso — Flujo Saga Completo (`INFO`)
Cuando un usuario se registra desde el Frontend (`POST /api/v1/auth/register`), el Orquestador valida la política de contraseña en `Identity`, crea el perfil en `Users` y persiste la credencial cifrada con BCrypt en `Identity`:
```log
2026-11-13 22:18:12.304 [http-nio-8080-exec-2] INFO  e.e.d.i.service.IdentityService - Política de contraseña validada correctamente
2026-11-13 22:18:12.640 [http-nio-8080-exec-2] INFO  e.e.d.i.service.IdentityService - Credencial creada exitosamente con hash BCrypt para userId=54941911-ef3b-402d-9256-40ced996294b
2026-11-13 22:18:12.648 [http-nio-8080-exec-2] INFO  e.e.d.orchestrator.AuthOrchestrator - Usuario registrado exitosamente con userId=54941911-ef3b-402d-9256-40ced996294b
```

---

### Escenario 3: Compensación Saga (Rollback Lógico) ante Fallo en Registro (`WARN` / `ERROR`)
Si la creación del perfil en `Users` tiene éxito pero el paso siguiente en `Identity` falla, el Orquestador ejecuta automáticamente el rollback eliminando el perfil en `Users` (`RF-OR-02`):
```log
2026-11-13 22:19:45.102 [http-nio-8080-exec-4] ERROR e.e.d.o.controller.GlobalExceptionHandler - Servicio de identidad no disponible: I/O error on POST request for "http://localhost:8085/api/v1/internal/identity/credentials"
2026-11-13 22:19:45.289 [http-nio-8080-exec-4] WARN  e.e.d.orchestrator.AuthOrchestrator - Compensación ejecutada: perfil eliminado en Users para userId=8ced579c-8be0-452f-b487-a414a4b0f471
```

---

### Escenario 4: Inicio de Sesión Exitoso y Emisión de JWT (`INFO`)
```log
2026-11-13 22:21:03.511 [http-nio-8080-exec-1] INFO  e.e.d.i.service.IdentityService - Autenticación exitosa y token JWT emitido (exp=1800s) para userId=54941911-ef3b-402d-9256-40ced996294b, roles=[CONTRATANTE]
2026-11-13 22:21:03.519 [http-nio-8080-exec-1] INFO  e.e.d.orchestrator.AuthOrchestrator - Inicio de sesión exitoso para userId=54941911-ef3b-402d-9256-40ced996294b
```

---

### Escenario 5: Rechazo de Inicio de Sesión y Bloqueo por Intentos Fallidos (`WARN`)
Cumpliendo con `RF-02` y `RNF-07`, ante credenciales inválidas se registra la alerta de seguridad sin exponer datos sensibles, y tras 5 intentos fallidos se bloquea temporalmente la cuenta (`423 LOCKED`):
```log
2026-11-13 22:23:10.114 [http-nio-8080-exec-5] WARN  e.e.d.i.service.IdentityService - Intento de login fallido (1/5) para userId=54941911-ef3b-402d-9256-40ced996294b
2026-11-13 22:23:10.118 [http-nio-8080-exec-5] WARN  e.e.d.o.controller.GlobalExceptionHandler - Excepción de seguridad: credenciales inválidas o usuario inactivo
2026-11-13 22:23:42.890 [http-nio-8080-exec-6] WARN  e.e.d.i.service.IdentityService - Cuenta bloqueada temporalmente por exceso de intentos fallidos (5/5) para userId=54941911-ef3b-402d-9256-40ced996294b
2026-11-13 22:23:42.895 [http-nio-8080-exec-6] WARN  e.e.d.o.controller.GlobalExceptionHandler - Excepción de seguridad: cuenta bloqueada
2026-11-13 22:24:15.004 [http-nio-8080-exec-7] WARN  e.e.d.orchestrator.AuthOrchestrator - Login rechazado para userId=99120411-ef3b-402d-9256-40ced9960000: cuenta inactiva o bloqueada (accountStatus=SUSPENDED)
```

---

### Escenario 6: Violaciones del Mecanismo de Seguridad JWT (`WARN`)
Cuando se intenta consumir un endpoint protegido sin token, con un token adulterado (`401 Unauthorized`) o con un rol insuficiente (`403 Forbidden`):
```log
2026-11-13 22:26:01.402 [http-nio-8080-exec-8] WARN  e.e.d.security.JwtAuthenticationFilter - Petición rechazada en GET /api/v1/users/me: Token JWT ausente en cabecera Authorization
2026-11-13 22:26:18.774 [http-nio-8080-exec-9] WARN  e.e.d.security.JwtAuthenticationFilter - Token JWT rechazado en GET /api/v1/users/me: JWT signature does not match locally computed signature
2026-11-13 22:26:35.910 [http-nio-8080-exec-10] WARN  o.s.s.w.a.AccessDeniedHandlerImpl - Acceso denegado (403 Forbidden) en GET /api/v1/auth/admin/ping para userId=54941911-ef3b-402d-9256-40ced996294b con roles=[ROLE_CONTRATANTE]
```

---

### Escenario 7: Recuperación y Cambio de Contraseña con Código OTP (`INFO` / `WARN`)
```log
2026-11-13 22:28:05.120 [http-nio-8080-exec-3] INFO  e.e.d.local.LocalNotificationService - [NOTIFY] Código de restablecimiento enviado a ana.contratante@oficioya.com (expira en 15 min)
2026-11-13 22:28:05.122 [http-nio-8080-exec-3] INFO  e.e.d.orchestrator.AuthOrchestrator - Código OTP de restablecimiento generado y enviado para userId=54941911-ef3b-402d-9256-40ced996294b
2026-11-13 22:29:14.601 [http-nio-8080-exec-4] INFO  e.e.d.orchestrator.AuthOrchestrator - Contraseña restablecida exitosamente para userId=54941911-ef3b-402d-9256-40ced996294b
2026-11-13 22:29:14.605 [http-nio-8080-exec-4] INFO  e.e.d.local.LocalNotificationService - [NOTIFY] Aviso de cambio de contraseña enviado a ana.contratante@oficioya.com
```

