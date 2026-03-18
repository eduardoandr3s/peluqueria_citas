# ✂️ Peluquería "Lalo Segovia" - API REST

Este repositorio contiene el backend de un sistema integral de gestión de citas y catálogo para una peluquería. Desarrollado como una API REST robusta, este proyecto está orientado a facilitar la reserva de servicios, gestión de horarios y administración de clientes, separando la lógica del lado del cliente y del administrador.

## 🚀 Tecnologías Utilizadas

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.0.3** (Framework principal)
* **PostgreSQL** (Base de datos relacional)
* **Spring Data JPA / Hibernate** (ORM para el mapeo de la base de datos)
* **Spring Security + JWT** (Autenticación stateless con tokens y autorización por roles)
* **BCrypt** (Encriptación unidireccional de contraseñas)
* **Spring Boot Validation** (Validación estricta de datos de entrada)
* **Maven** (Gestor de dependencias y construcción)
* **Lombok** (Optimización y reducción de código *boilerplate*)

## ✨ Características Implementadas

* **Arquitectura Multicapa:** Separación clara entre Controladores (API), Servicios (Lógica de negocio) y Repositorios (Acceso a datos).
* **Autenticación JWT con Roles:** Sistema de login/registro con tokens JWT. Dos roles implementados: `USER` (clientes) y `ADMIN` (administrador). Cada endpoint está protegido según el rol requerido.
* **Patrón DTO (Data Transfer Object):** Implementado en todas las entidades (Usuario, Servicio, Cita) con DTOs separados para creación y actualización parcial. Garantiza que información sensible no se exponga en las respuestas.
* **Validación de Conflictos de Horarios:** El sistema impide agendar citas que se solapen con otras ya existentes, calculando el rango de tiempo según la duración del servicio.
* **Validación de Horario Laboral:** Las citas solo se pueden agendar de Lunes a Sábado entre 9:00 y 20:00, y no se permiten citas en el pasado.
* **Soft Delete:** Los usuarios y servicios no se eliminan físicamente, se marcan como inactivos. Esto preserva el historial de citas para estadísticas y auditoría.
* **Validación de Unicidad de Email:** Se verifica tanto al crear como al actualizar que no exista otro usuario con el mismo email.
* **Manejo Global de Excepciones:** `@RestControllerAdvice` con handlers específicos para validación (400), recurso no encontrado (404), conflictos (409) y un handler genérico (500) que no expone información interna.
* **Perfiles de Configuración:** Separación entre entorno de desarrollo (`dev`) y producción (`prod`) con configuraciones específicas para cada uno.
* **Tipado Estricto con Enums:** Estado de citas (`PENDIENTE`, `CONFIRMADA`, `ANULADA`) y roles (`USER`, `ADMIN`) mediante enumeraciones.

## 🔐 Endpoints de la API

### Autenticación (público)
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/auth/registro` | Registrar nuevo usuario |
| POST | `/api/auth/login` | Iniciar sesión (devuelve token JWT) |

### Servicios
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/servicios` | Público | Listar servicios activos |
| GET | `/api/servicios/{id}` | Público | Obtener servicio por ID |
| POST | `/api/servicios` | ADMIN | Crear servicio |
| PUT | `/api/servicios/{id}` | ADMIN | Actualizar servicio |
| DELETE | `/api/servicios/{id}` | ADMIN | Desactivar servicio (soft delete) |

### Usuarios
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/usuarios` | ADMIN | Listar usuarios activos |
| GET | `/api/usuarios/{id}` | USER/ADMIN | Obtener usuario por ID |
| PUT | `/api/usuarios/{id}` | USER/ADMIN | Actualizar usuario |
| DELETE | `/api/usuarios/{id}` | ADMIN | Desactivar usuario (soft delete) |

### Citas
| Método | Endpoint | Acceso | Descripción |
|--------|----------|--------|-------------|
| GET | `/api/citas` | USER/ADMIN | Listar citas |
| GET | `/api/citas/{id}` | USER/ADMIN | Obtener cita por ID |
| POST | `/api/citas` | USER/ADMIN | Agendar cita |
| PUT | `/api/citas/{id}` | USER/ADMIN | Actualizar cita |
| DELETE | `/api/citas/{id}` | USER/ADMIN | Eliminar cita |

## 🗄️ Modelo de Datos

* **`usuarios`**: Clientes y administradores. Almacena nombre, email (único), teléfono, contraseña (encriptada), rol y estado activo/inactivo.
* **`servicios`**: Catálogo de la peluquería (ej. cortes, tinturas, alisados). Almacena descripción, duración en minutos, precio y estado activo/inactivo.
* **`citas`**: Entidad transaccional que vincula a un `usuario` con un `servicio` en una `fechaHora` específica. Gestiona su estado mediante un `Enum` (`PENDIENTE`, `CONFIRMADA`, `ANULADA`).

## ⚙️ Configuración y Puesta en Marcha

### Prerrequisitos
* Java Development Kit (JDK) 21 instalado en tu máquina.
* PostgreSQL instalado y corriendo en el puerto local `5432`.
* Git para la clonación del repositorio.

### Instalación local

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/eduardoandr3s/peluqueria_citas.git
    cd peluqueria_citas
    ```

2.  **Preparar la Base de Datos:**
    Abre tu gestor de base de datos (ej. pgAdmin o DBeaver) y crea una base de datos vacía con el nombre:
    ```sql
    CREATE DATABASE peluqueria_db;
    ```

3.  **Configurar Variables de Entorno:**
    El proyecto utiliza variables de entorno para proteger credenciales. Configura las siguientes en tu sistema o IDE:
    * `DB_USERNAME`: Tu usuario de PostgreSQL.
    * `DB_PASSWORD`: Tu contraseña de PostgreSQL.
    * `JWT_SECRET`: Clave secreta para firmar tokens JWT (mínimo 32 caracteres).

    *(Nota: El perfil `dev` usa `ddl-auto=update`, que crea y actualiza las tablas automáticamente al iniciar.)*

4.  **Ejecutar la aplicación:**
    ```bash
    ./mvnw spring-boot:run
    ```
    La API estará disponible en `http://localhost:8080`.

5.  **Crear un usuario ADMIN (opcional):**
    Después de registrar un usuario, promuévelo a ADMIN directamente en PostgreSQL:
    ```sql
    UPDATE usuarios SET rol = 'ADMIN' WHERE email = 'tu-email@ejemplo.com';
    ```

## 🛣️ Roadmap / Próximos Pasos

- [x] Arquitectura inicial, dependencias y conexión a PostgreSQL.
- [x] Creación de entidades ORM (`Servicio`, `Usuario`, `Cita`) con relaciones `@ManyToOne`.
- [x] Implementación del Patrón DTO, Validaciones de entrada y Manejo Global de Excepciones.
- [x] CRUD completo (GET, POST, PUT, DELETE) para todas las entidades.
- [x] Encriptación de contraseñas (BCrypt) e implementación de Spring Security.
- [x] Variables de entorno para credenciales sensibles.
- [x] Refactorización y estandarización de código a `camelCase`.
- [x] Validación de conflictos de horarios en citas.
- [x] Validación de fecha futura y horario laboral (L-S, 9:00-20:00).
- [x] Validación de unicidad de email al crear y actualizar usuarios.
- [x] Soft delete en usuarios y servicios.
- [x] DTOs con validaciones (`@Valid`) para todas las entidades.
- [x] Handler genérico de excepciones (sin exponer stack traces).
- [x] Perfiles de configuración separados (dev/prod).
- [x] Autenticación JWT y autorización por roles (USER/ADMIN).
- [ ] Desarrollo del Frontend interactivo consumiendo esta API.

---
*Desarrollado por Eduardo Andrés Segovia Román.*
