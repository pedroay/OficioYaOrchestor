# 📄 Requerimientos del Microservicio

## 1. Lista general de requerimientos

El sistema de OFICIOYA tiene los siguientes requerimientos (descripción a alto nivel):

### 1.1 Requerimientos funcionales

El sistema de OFICIOYA debe tener la capacidad de:

1. El sistema debe permitir el inicio de sesión mediante usuario y contraseña, validando las credenciales contra la información de usuario gestionada por el User domain.

2. El sistema debe rechazar el inicio de sesión con credenciales inválidas o usuario inactivo, devolviendo un mensaje de error apropiado sin filtrar información sensible.

3. El sistema debe permitir a un usuario autenticado cambiar su contraseña, validando previamente su contraseña actual.

4. El sistema debe aplicar una política de complejidad a las nuevas contraseñas (longitud mínima, combinación de caracteres).

5. El sistema debe soportar la asignación de uno o varios roles a un mismo usuario (Trabajador, Contratante, Administrador).

6. El sistema debe restringir el acceso a funcionalidades según el rol del usuario autenticado.

7. Al autenticarse correctamente, el sistema debe generar un token JWT que incluya el id del usuario, sus roles y una fecha de expiración.

8. El sistema debe cifrar (hash) cualquier contraseña que gestione (por ejemplo, al actualizarla) antes de persistirla o compararla.


### 1.2 Requerimientos no funcionales

El sistema de OFICIOYA debe tener:

1. Seguridad: toda autenticación debe emitir y validar tokens firmados mediante JWT.

2. Calidad: la cobertura mínima de pruebas unitarias debe ser del 80%.

3. Logging: el servicio debe generar logs estructurados para trazabilidad y debugging.

4. Las contraseñas gestionadas por el servicio deben almacenarse siempre cifradas (hash), nunca en texto plano.

5. Los tokens JWT deben tener un tiempo de expiración definido y deben ser rechazados una vez vencidos.

6. El servicio debe documentar sus APIs (Swagger/OpenAPI) para consumo del Orchestrator y otros squads.

7. El servicio debe responder con mensajes de error genéricos ante credenciales inválidas, sin revelar si el fallo fue por usuario inexistente o contraseña incorrecta (evita enumeración de usuarios).

## 2. Diagramas de caso de uso

### 2.1 Requerimiento Funcional 1

| Campo | Descripción |
|------|-------------|
| **ID** | RF-01 |
| **Nombre del requerimiento** | INICION DE SESIÓN  |
| **Descripción** | El sistema debe permitir el inicio de sesión mediante usuario y contraseña, validando las credenciales contra la información de usuario gestionada por el User domain. Cada usuario debe ser unico, esto se validará por el microservicio USER DOMAIN donde deben validar esto al momento de crearla, la contraseña debe cumplir unos requisistos, si no se cumplen los requisitos , se rechazara automaticamente |
| **Precondiciones** | Para que el sistema cumpla con este requerimiento,primero el usuario ya debio ser creado, debe ademas tener una contraseña que cumpla con los requisitos de seguridad estipulados . |
| **Actor** |user |
| **Flujo principal** | 1. el usuario ingresa a la aplicación 2. el usaurio ingresas sus credenciales (user y password) 3. se revisa si password ingresada cumple con los requisitos pleaneados 4. se realiza el proceso de autenticación  | 
| **Diagrama de caso de uso** | ![Diagrama de caso de uso - Gestión del Torneo](../uml/CaseOfUse_GestionTorneo.png) |
| **Poscondiciones** | Se espera como resultado que el torneo haya sido creado, actualizado o que su estado haya cambiado exitosamente en el sistema, reflejando la información actualizada para todos los usuarios. |


### 2.2 Requerimiento Funcional 2

| Campo | Descripción |
|------|-------------|
| **ID** | RF-02 |
| **Nombre del requerimiento** | Registrar Equipo |
| **Descripción** | El sistema debe permitir a los capitanes registrar un equipo en el torneo que se encuentre activo, ingresando la información del equipo y de sus integrantes para poder participar en el torneo junto a sus compañeros. |
| **Precondiciones** | Para que el sistema cumpla con este requerimiento, TechCup debe tener previamente un capitán autenticado con credenciales válidas (nombre de usuario y contraseña) y debe existir un torneo en estado *Active* en el cual inscribir al equipo. |
| **Actor** | Capitán (Captain) |
| **Flujo principal** | 1. El capitán inicia sesión en el sistema con sus credenciales.<br>2. El capitán selecciona la opción de registrar equipo.<br>3. El sistema verifica que exista un torneo en estado *Active*.<br>4. El sistema muestra el formulario de registro de equipo.<br>5. El capitán ingresa los datos del equipo (nombre, integrantes/compañeros).<br>6. El sistema valida la información ingresada.<br>7. El sistema registra el equipo y lo asocia al torneo activo.<br>8. El sistema muestra una confirmación del registro exitoso del equipo. |
| **Diagrama de caso de uso** | ![Diagrama de caso de uso - Registrar Equipo](../uml/CaseOfUse_RegistrarEquipo.png) |
| **Poscondiciones** | Se espera como resultado que el equipo quede registrado e inscrito exitosamente en el torneo activo, siendo visible para los organizadores y demás usuarios del sistema. |

### 2.3 Requerimiento Funcional 3

| Campo | Descripción |
|------|-------------|
| **ID** | RF-03 |
| **Nombre del requerimiento** | Pagar Inscripción del Torneo |
| **Descripción** | El sistema debe permitir a los capitanes realizar el pago de la inscripción de su equipo en el torneo a través de la pasarela de pagos PSE, para quedar registrados formalmente y poder participar en el torneo. |
| **Precondiciones** | Para que el sistema cumpla con este requerimiento, TechCup debe tener previamente un capitán autenticado con credenciales válidas, un equipo registrado en el torneo activo y la integración con el sistema externo PSE disponible para procesar la transacción. |
| **Actor** | Capitán (Captain) |
| **Flujo principal** | 1. El capitán inicia sesión en el sistema con sus credenciales.<br>2. El capitán selecciona la opción de pagar la inscripción del torneo.<br>3. El sistema muestra los detalles del pago (tarifa de inscripción, equipo asociado, torneo).<br>4. El capitán confirma el pago.<br>5. El sistema redirige al capitán a la pasarela de pagos PSE.<br>6. El capitán completa la transacción en PSE.<br>7. PSE notifica al sistema el resultado de la transacción (aprobada/rechazada).<br>8. El sistema actualiza el estado de pago del equipo.<br>9. El sistema muestra una confirmación del pago realizado al capitán. |
| **Diagrama de caso de uso** | ![Diagrama de caso de uso - Pagar Inscripción](../uml/CaseOfUse_PagarTorneo.png) |
| **Poscondiciones** | Se espera como resultado que el pago de la inscripción quede registrado exitosamente en el sistema, el equipo quede formalmente inscrito en el torneo y el comprobante de pago esté disponible para consulta y validación por parte de los organizadores. |



## 4. Mockup

### Requerimiento funcional seleccionado: RF-02 — Registrar Equipo

Se seleccionó el requerimiento funcional **RF-02 (Registrar Equipo)** para el diseño de los mockups:

### Diseño de Mockups — Flujo de Registro de Equipo

Los mockups del flujo de navegación para el registro de un equipo fueron diseñados en **Figma** y pueden consultarse en el siguiente enlace:

🔗 **[Ver Mockups en Figma — Flujo de Registro de Equipo](https://www.figma.com/design/NvDN2mIs8itoVR14fFOCbU/Flujo-Registro?node-id=0-1&t=sMDkfjVEIjFY629o-1)**