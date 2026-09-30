# OficioYa — Planeación Squad 1
### Épicas, Historias de Usuario y Tareas

**Squad 1:** Samuel A, Pedro A, Javier C, Juanita R.
**Dominios a cargo:** Identity domain · Orchestrator / API Gateway · Home
**Sprint 1** (Semana 8-9, según cronograma del proyecto)
    

---

## ÉPICA 1 — Identity Domain
**Objetivo:** Gestionar la identidad y seguridad de acceso de las personas que usan OficioYa, permitiendo que un mismo usuario actúe como Contratante y/o Trabajador, con soporte de rol administrador.

**Reglas de negocio:**
- Una persona puede ser Contratante y Trabajador (una sola cuenta).
- Existe un rol administrador.

### HU 1.1 — Registro de usuario
*Como* persona nueva en la plataforma, *quiero* registrarme con usuario y contraseña *para* poder acceder a OficioYa como Trabajador y/o Contratante.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Diseñar modelo de datos de usuario (entidad, atributos, relación con roles) | Samuel A |
| 2 | Endpoint `POST /auth/register` con validaciones de entrada | Pedro A |
| 3 | Hash de contraseña (bcrypt/BCryptPasswordEncoder) antes de persistir | Javier C |
| 4 | Pruebas unitarias del registro (casos válidos e inválidos) | Juanita R |

### HU 1.2 — Inicio de sesión
*Como* usuario registrado, *quiero* iniciar sesión con usuario y contraseña *para* obtener acceso autenticado al sistema.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Endpoint `POST /auth/login` con validación de credenciales | Pedro A |
| 2 | Generación de JWT al autenticar (claims: id, roles, expiración) | Javier C |
| 3 | Manejo de errores (credenciales inválidas, usuario inactivo) | Juanita R |
| 4 | Pruebas unitarias e integración del login | Samuel A |

### HU 1.3 — Gestión de credenciales
*Como* usuario, *quiero* poder actualizar o recuperar mi contraseña *para* mantener segura mi cuenta.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Endpoint de cambio de contraseña (usuario autenticado) | Juanita R |
| 2 | Validaciones de seguridad (contraseña actual, política de complejidad) | Samuel A |
| 3 | Pruebas unitarias | Pedro A |

### HU 1.4 — Generación y validación de JWT
*Como* sistema, *quiero* generar y validar tokens JWT *para* garantizar que solo usuarios autenticados accedan a recursos protegidos.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Servicio de generación de JWT (firma, expiración, refresh si aplica) | Javier C |
| 2 | Servicio de validación de JWT (firma, expiración, claims) | Samuel A |
| 3 | Endpoint interno/expuesto para validar token (consumido por el Orchestrator) | Pedro A |
| 4 | Pruebas unitarias de generación/validación | Juanita R |

### HU 1.5 — Roles y autorización
*Como* administrador del sistema, *quiero* que existan roles (Trabajador, Contratante, Administrador) *para* controlar el acceso a funcionalidades según el tipo de usuario.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Modelo de roles y asociación usuario-rol (soporta multi-rol) | Samuel A |
| 2 | Lógica de autorización por rol (anotaciones/guards en endpoints) | Javier C |
| 3 | Creación de usuario administrador semilla (seed) | Pedro A |
| 4 | Pruebas de autorización (acceso permitido/denegado por rol) | Juanita R |

---

## ÉPICA 2 — Orchestrator / API Gateway
**Objetivo:** Ser el punto único de entrada del backend, enrutando solicitudes del frontend hacia los microservicios, validando JWT y ocultando la topología interna del sistema.

### HU 2.1 — Enrutamiento de solicitudes
*Como* frontend, *quiero* enviar todas mis solicitudes a un único punto de entrada *para* no depender de conocer la ubicación de cada microservicio.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Configuración base del Gateway (Spring Cloud Gateway o similar) | Pedro A |
| 2 | Definición de rutas hacia Identity, User, Worker, Service, Search, Reputation, Reporting, Notification | Samuel A |
| 3 | Pruebas de enrutamiento por dominio | Javier C |

### HU 2.2 — Validación de JWT (filtro de seguridad)
*Como* sistema, *quiero* validar el JWT en cada solicitud entrante *para* bloquear el acceso no autorizado antes de llegar a los microservicios.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Filtro global de validación de JWT (invoca a Identity o valida localmente) | Javier C |
| 2 | Definición de rutas públicas vs. protegidas (login, registro, home) | Juanita R |
| 3 | Propagación de contexto de usuario (headers) hacia microservicios internos | Samuel A |
| 4 | Pruebas del filtro (token válido, inválido, expirado, ausente) | Pedro A |

### HU 2.3 — Políticas de acceso y manejo de errores
*Como* administrador del sistema, *quiero* que el Gateway aplique políticas generales de acceso y maneje errores de integración *para* dar respuestas consistentes ante fallos.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Políticas de acceso por rol a nivel de gateway (autorización básica) | Juanita R |
| 2 | Manejador global de excepciones (timeouts, servicio caído, 4xx/5xx) | Pedro A |
| 3 | Logging centralizado de solicitudes/respuestas | Samuel A |
| 4 | Pruebas de escenarios de error | Javier C |

### HU 2.4 — Coordinación entre servicios
*Como* Gateway, *quiero* poder coordinar llamadas entre servicios cuando una solicitud lo requiera *para* simplificar la lógica del frontend.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Identificar flujos que requieren orquestación (ej. búsqueda + perfil) | Samuel A |
| 2 | Implementar llamadas compuestas/orquestadas donde aplique | Javier C |
| 3 | Pruebas de integración de flujos orquestados | Juanita R |

---

## ÉPICA 3 — Home
**Objetivo:** Ofrecer una página de inicio pública que presente OficioYa y dirija al usuario hacia registro/login.

> Este ítem no tiene alcance detallado en los documentos entregados por el profesor. Se sugiere confirmar el alcance exacto con docencia; mientras tanto se plantea un alcance razonable basado en el objetivo general del proyecto.

### HU 3.1 — Landing page pública
*Como* visitante, *quiero* ver una página de inicio que explique qué es OficioYa *para* entender el propósito de la plataforma antes de registrarme.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Definir contenido y estructura de la landing (propuesta de valor, cómo funciona) | Juanita R |
| 2 | Maquetación del componente Home (frontend) | Pedro A |
| 3 | Botones de acceso a login/registro | Samuel A |

### HU 3.2 — Navegación inicial
*Como* visitante, *quiero* poder navegar fácilmente hacia login o registro desde el home *para* comenzar a usar la plataforma.

| # | Tarea | Responsable |
|---|---|---|
| 1 | Enlaces/rutas hacia login y registro | Javier C |
| 2 | Validar responsive (requisito no funcional del proyecto) | Juanita R |

---

## Tareas transversales (no funcionales, aplican a las 3 épicas)

| # | Tarea | Responsable |
|---|---|---|
| 1 | Configurar cobertura mínima de pruebas del 80% (JaCoCo u otra herramienta) | Samuel A |
| 2 | Configurar logging estructurado en Identity y Orchestrator | Javier C |
| 3 | Validar diseño responsive en Home | Pedro A |
| 4 | Documentar contratos de API (Swagger/OpenAPI) de Identity y Orchestrator | Juanita R |

---

## Resumen de carga por integrante (aprox.)

| Integrante | Tareas asignadas |
|---|---|
| Samuel A | 9 |
| Pedro A | 8 |
| Javier C | 9 |
| Juanita R | 9 |

La carga quedó balanceada entre los 4 integrantes (8-9 tareas cada uno en este primer sprint). Recuerden que, según las reglas del curso, **todos deben apoyar la implementación** independientemente del rol asignado, y que cada tarea creada en Jira debe quedar asignada a una persona del squad.
