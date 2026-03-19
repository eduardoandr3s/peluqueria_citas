# Peluqueria "Lalo Segovia" - API REST

Este repositorio contiene el backend de un sistema integral de gestion de citas y catalogo para una peluqueria. Desarrollado como una API REST robusta, este proyecto esta orientado a facilitar la reserva de servicios, gestion de horarios y administracion de clientes, separando la logica del lado del cliente y del administrador.

## Tecnologias Utilizadas

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.0.3** (Framework principal)
* **PostgreSQL** (Base de datos relacional)
* **Spring Data JPA / Hibernate** (ORM para el mapeo de la base de datos)
* **Spring Security + JWT** (Autenticacion stateless con tokens y autorizacion por roles)
* **BCrypt** (Encriptacion unidireccional de contrasenas)
* **Spring Boot Validation** (Validacion estricta de datos de entrada)
* **Maven** (Gestor de dependencias y construccion)
* **Lombok** (Reduccion de codigo boilerplate)
* **JUnit 5 + Mockito** (Testing unitario)

## Caracteristicas Implementadas

* **Arquitectura por Dominio:** Organizacion del codigo por modulo de negocio (`usuario/`, `cita/`, `servicio/`, `auth/`, `security/`), donde cada modulo agrupa su modelo, controller, service, repository y DTOs.
* **Constructor Injection:** Inyeccion de dependencias via constructor con campos `final` (sin `@Autowired`), siguiendo las mejores practicas de Spring para inmutabilidad y testabilidad.
* **Autenticacion JWT con Roles:** Sistema de login/registro con tokens JWT. Dos roles implementados: `USER` (clientes) y `ADMIN` (administrador). Cada endpoint esta protegido segun el rol requerido.
* **Patron DTO (Data Transfer Object):** Implementado en todas las entidades (Usuario, Servicio, Cita) con DTOs separados para creacion y actualizacion parcial. Garantiza que informacion sensible no se exponga en las respuestas.
* **Validacion de Conflictos de Horarios:** El sistema impide agendar citas que se solapen con otras ya existentes, calculando el rango de tiempo segun la duracion del servicio.
* **Validacion de Horario Laboral:** Las citas solo se pueden agendar de Lunes a Sabado entre 9:00 y 20:00, y no se permiten citas en el pasado.
* **Soft Delete:** Los usuarios y servicios no se eliminan fisicamente, se marcan como inactivos. Esto preserva el historial de citas para estadisticas y auditoria.
* **Validacion de Unicidad de Email:** Se verifica tanto al crear como al actualizar que no exista otro usuario con el mismo email.
* **Manejo Global de Excepciones:** `@RestControllerAdvice` con handlers especificos para validacion (400), recurso no encontrado (404), conflictos (409) y un handler generico (500) que no expone informacion interna.
* **Perfiles de Configuracion:** Separacion entre entorno de desarrollo (`dev`) y produccion (`prod`) con configuraciones especificas para cada uno.
* **Tipado Estricto con Enums:** Estado de citas (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) y roles (`USER`, `ADMIN`) mediante enumeraciones.
* **Suite de Tests Unitarios (59 tests):** Cobertura completa de logica de negocio sin depender de Spring context ni base de datos.

## Estructura del Proyecto

```
com.segovia.peluqueria/
├── usuario/                  # Modulo de usuarios
│   ├── Usuario.java          # Entidad JPA
│   ├── Rol.java              # Enum (USER, ADMIN)
│   ├── UsuarioController.java
│   ├── UsuarioService.java
│   ├── UsuarioRepository.java
│   └── dto/
│       ├── UsuarioRequestDTO.java
│       ├── UsuarioUpdateDTO.java
│       └── UsuarioResponseDTO.java
├── cita/                     # Modulo de citas
│   ├── Cita.java
│   ├── EstadoCita.java       # Enum (PENDIENTE, CONFIRMADA, ANULADA)
│   ├── CitaController.java
│   ├── CitaService.java
│   ├── CitaRepository.java
│   └── dto/
│       ├── CitaRequestDTO.java
│       └── CitaUpdateDTO.java
├── servicio/                 # Modulo de servicios
│   ├── Servicio.java
│   ├── ServicioController.java
│   ├── ServicioService.java
│   ├── ServicioRepository.java
│   └── dto/
│       ├── ServicioRequestDTO.java
│       └── ServicioUpdateDTO.java
├── auth/                     # Modulo de autenticacion
│   ├── AuthController.java
│   └── dto/
│       ├── AuthResponseDTO.java
│       └── LoginRequestDTO.java
├── security/                 # Configuracion de seguridad
│   ├── SecurityConfig.java
│   ├── JwtService.java
│   ├── JwtAuthenticationFilter.java
│   └── CustomUserDetailsService.java
└── exception/                # Excepciones compartidas
    ├── GlobalExceptionHandler.java
    ├── ResourceNotFoundException.java
    └── ConflictoHorarioException.java
```

## Tests

59 unit tests que cubren toda la logica de negocio, ejecutandose sin Spring context ni base de datos (~2 segundos):

| Clase | Tests | Cobertura |
|-------|-------|-----------|
| UsuarioServiceTest | 10 | CRUD, email duplicado, encriptacion, soft delete |
| CitaServiceTest | 18 | Agendar, horarios, conflictos, CRUD completo |
| ServicioServiceTest | 9 | CRUD, soft delete |
| JwtServiceTest | 8 | Generar/extraer/validar tokens, firmas |
| AuthControllerTest | 5 | Login, registro, credenciales invalidas |
| CustomUserDetailsServiceTest | 4 | Carga de usuario, roles, estado |
| JwtAuthenticationFilterTest | 5 | Filtro con/sin token, token invalido/expirado |

Para ejecutar los tests:
```bash
./mvnw test
```

## Endpoints de la API

### Autenticacion (publico)
| Metodo | Endpoint | Descripcion |
|--------|----------|-------------|
| POST | `/api/auth/registro` | Registrar nuevo usuario |
| POST | `/api/auth/login` | Iniciar sesion (devuelve token JWT) |

### Servicios
| Metodo | Endpoint | Acceso | Descripcion |
|--------|----------|--------|-------------|
| GET | `/api/servicios` | Publico | Listar servicios activos |
| GET | `/api/servicios/{id}` | Publico | Obtener servicio por ID |
| POST | `/api/servicios` | ADMIN | Crear servicio |
| PUT | `/api/servicios/{id}` | ADMIN | Actualizar servicio |
| DELETE | `/api/servicios/{id}` | ADMIN | Desactivar servicio (soft delete) |

### Usuarios
| Metodo | Endpoint | Acceso | Descripcion |
|--------|----------|--------|-------------|
| GET | `/api/usuarios` | ADMIN | Listar usuarios activos |
| GET | `/api/usuarios/{id}` | USER/ADMIN | Obtener usuario por ID |
| PUT | `/api/usuarios/{id}` | USER/ADMIN | Actualizar usuario |
| DELETE | `/api/usuarios/{id}` | ADMIN | Desactivar usuario (soft delete) |

### Citas
| Metodo | Endpoint | Acceso | Descripcion |
|--------|----------|--------|-------------|
| GET | `/api/citas` | USER/ADMIN | Listar citas |
| GET | `/api/citas/{id}` | USER/ADMIN | Obtener cita por ID |
| POST | `/api/citas` | USER/ADMIN | Agendar cita |
| PUT | `/api/citas/{id}` | USER/ADMIN | Actualizar cita |
| DELETE | `/api/citas/{id}` | USER/ADMIN | Eliminar cita |

## Modelo de Datos

* **`usuarios`**: Clientes y administradores. Almacena nombre, email (unico), telefono, contrasena (encriptada), rol y estado activo/inactivo.
* **`servicios`**: Catalogo de la peluqueria (ej. cortes, tinturas, alisados). Almacena descripcion, duracion en minutos, precio y estado activo/inactivo.
* **`citas`**: Entidad transaccional que vincula a un `usuario` con un `servicio` en una `fechaHora` especifica. Gestiona su estado mediante un `Enum` (`PENDIENTE`, `CONFIRMADA`, `ANULADA`).

## Configuracion y Puesta en Marcha

### Prerrequisitos
* Java Development Kit (JDK) 21 instalado en tu maquina.
* PostgreSQL instalado y corriendo en el puerto local `5432`.
* Git para la clonacion del repositorio.

### Instalacion local

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/eduardoandr3s/peluqueria_citas.git
    cd peluqueria_citas
    ```

2.  **Preparar la Base de Datos:**
    Abre tu gestor de base de datos (ej. pgAdmin o DBeaver) y crea una base de datos vacia con el nombre:
    ```sql
    CREATE DATABASE peluqueria_db;
    ```

3.  **Configurar Variables de Entorno:**
    El proyecto utiliza variables de entorno para proteger credenciales. Configura las siguientes en tu sistema o IDE:
    * `DB_USERNAME`: Tu usuario de PostgreSQL.
    * `DB_PASSWORD`: Tu contrasena de PostgreSQL.
    * `JWT_SECRET`: Clave secreta para firmar tokens JWT (minimo 32 caracteres).

    *(Nota: El perfil `dev` usa `ddl-auto=update`, que crea y actualiza las tablas automaticamente al iniciar.)*

4.  **Ejecutar la aplicacion:**
    ```bash
    ./mvnw spring-boot:run
    ```
    La API estara disponible en `http://localhost:8080`.

5.  **Crear un usuario ADMIN (opcional):**
    Despues de registrar un usuario, promuevelo a ADMIN directamente en PostgreSQL:
    ```sql
    UPDATE usuarios SET rol = 'ADMIN' WHERE email = 'tu-email@ejemplo.com';
    ```

## Roadmap / Proximos Pasos

- [x] Arquitectura inicial, dependencias y conexion a PostgreSQL.
- [x] Creacion de entidades ORM (`Servicio`, `Usuario`, `Cita`) con relaciones `@ManyToOne`.
- [x] Implementacion del Patron DTO, Validaciones de entrada y Manejo Global de Excepciones.
- [x] CRUD completo (GET, POST, PUT, DELETE) para todas las entidades.
- [x] Encriptacion de contrasenas (BCrypt) e implementacion de Spring Security.
- [x] Variables de entorno para credenciales sensibles.
- [x] Refactorizacion y estandarizacion de codigo a `camelCase`.
- [x] Validacion de conflictos de horarios en citas.
- [x] Validacion de fecha futura y horario laboral (L-S, 9:00-20:00).
- [x] Validacion de unicidad de email al crear y actualizar usuarios.
- [x] Soft delete en usuarios y servicios.
- [x] DTOs con validaciones (`@Valid`) para todas las entidades.
- [x] Handler generico de excepciones (sin exponer stack traces).
- [x] Perfiles de configuracion separados (dev/prod).
- [x] Autenticacion JWT y autorizacion por roles (USER/ADMIN).
- [x] Constructor injection y eliminacion de `@Data` en entidades JPA.
- [x] Reestructuracion de paquetes por dominio (usuario, cita, servicio, auth, security).
- [x] Suite de 59 unit tests (services, security, controller).
- [ ] Desarrollo del Frontend interactivo consumiendo esta API.

---
*Desarrollado por Eduardo Andres Segovia Roman.*
