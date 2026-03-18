# ✂️ Peluquería "Lalo Segovia" - API REST

Este repositorio contiene el backend de un sistema integral de gestión de citas y catálogo para una peluquería. Desarrollado como una API REST robusta, este proyecto está orientado a facilitar la reserva de servicios, gestión de horarios y administración de clientes, separando la lógica del lado del cliente y del administrador.

## 🚀 Tecnologías Utilizadas

El núcleo del proyecto está construido bajo estándares actuales de la industria, garantizando un buen rendimiento y escalabilidad:

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.0.3** (Framework principal)
* **PostgreSQL** (Base de datos relacional)
* **Spring Data JPA / Hibernate** (ORM para el mapeo de la base de datos)
* **Spring Security & BCrypt** (Encriptación de credenciales y seguridad de la API)
* **Spring Boot Validation** (Validación estricta de datos de entrada)
* **Maven** (Gestor de dependencias y construcción)
* **Lombok** (Optimización y reducción de código *boilerplate*)

## ✨ Características Implementadas

* **Arquitectura Multicapa:** Separación clara entre Controladores (API), Servicios (Lógica de negocio) y Repositorios (Acceso a datos).
* **Seguridad y Encriptación:** Uso de Spring Security y `BCryptPasswordEncoder` para encriptar las contraseñas de los usuarios en la base de datos de forma unidireccional.
* **Patrón DTO (Data Transfer Object):** Implementado para la entidad `Usuario`, garantizando que información sensible no se exponga, y permitiendo actualizaciones parciales (Update DTO) sin exigir campos obligatorios.
* **Validación de Conflictos de Horarios:** El sistema impide agendar citas que se solapen con otras ya existentes, calculando el rango de tiempo según la duración del servicio. Aplica tanto al crear como al actualizar citas, y excluye citas anuladas.
* **Manejo Global de Excepciones:** Uso de `@RestControllerAdvice` para capturar y estandarizar las respuestas de error (Ej: 404 para recursos no encontrados, 400 para errores de validación, 409 para conflictos de horario o integridad en la base de datos).
* **Validación de Datos:** Uso de anotaciones como `@NotBlank`, `@Email` y `@Size` para proteger la integridad de los datos antes de llegar a la base de datos.
* **Tipado Estricto con Enums:** Blindaje del estado de las citas mediante enumeraciones (`PENDIENTE`, `CONFIRMADA`, `ANULADA`).
* **Estándares Clean Code:** Refactorización de la API para utilizar convenciones `camelCase` en el modelo y DTOs, garantizando una serialización JSON limpia para el Frontend, manteniendo la compatibilidad `snake_case` en PostgreSQL.

## 🗄️ Modelo de Datos

La arquitectura de la información se basa en un esquema relacional limpio con tres entidades principales:

* **`servicios`**: Catálogo de la peluquería (ej. cortes, tinturas, alisados). Almacena descripción, duración en minutos y precio.
* **`usuarios`**: Gestión de clientes registrados, almacenando datos de contacto y credenciales de acceso.
* **`citas`**: Entidad transaccional que vincula a un `usuario` con un `servicio` en una `fechaHora` específica. Gestiona su estado mediante un `Enum` mapeado a texto.

## ⚙️ Configuración y Puesta en Marcha

### Prerrequisitos
* Java Development Kit (JDK) 21 instalado en tu máquina.
* PostgreSQL instalado y corriendo en el puerto local `5432`.
* Git para la clonación del repositorio.

### Instalación local

1.  **Clonar el repositorio:**
    ```bash
    git clone [https://github.com/eduardoandr3s/peluqueria_citas.git](https://github.com/eduardoandr3s/peluqueria_citas.git)
    cd peluqueria_citas
    ```

2.  **Preparar la Base de Datos:**
    Abre tu gestor de base de datos (ej. pgAdmin o DBeaver) y crea una base de datos vacía con el nombre:
    ```sql
    CREATE DATABASE peluqueria_db;
    ```

3.  **Configurar credenciales (Variables de Entorno):**
    El proyecto utiliza variables de entorno para proteger las credenciales de la base de datos. Antes de ejecutar, asegúrate de configurar las siguientes variables en tu sistema o IDE:
    * `DB_USERNAME`: Tu usuario de PostgreSQL.
    * `DB_PASSWORD`: Tu contraseña de PostgreSQL.

    *(Nota: La propiedad `spring.jpa.hibernate.ddl-auto=update` se encargará de crear y actualizar las tablas automáticamente al iniciar la aplicación).*

4.  **Ejecutar la aplicación:**
    Puedes ejecutar el proyecto directamente desde tu IDE (como IntelliJ IDEA) corriendo la clase `PeluqueriaApplication`, o mediante la terminal en la raíz del proyecto:
    ```bash
    ./mvnw spring-boot:run
    ```
    La API estará disponible en `http://localhost:8080`.

## 🛣️ Roadmap / Próximos Pasos

- [x] Arquitectura inicial, dependencias y conexión a PostgreSQL.
- [x] Creación de entidades ORM (`Servicio`, `Usuario`, `Cita`) con relaciones `@ManyToOne`.
- [x] Implementación del Patrón DTO, Validaciones de entrada y Manejo Global de Excepciones.
- [x] CRUD completo (GET, POST, PUT, DELETE) para todas las entidades.
- [x] Creación de archivo `peticiones.http` para pruebas locales integradas.
- [x] Encriptación de contraseñas (BCrypt) e implementación de Spring Security.
- [x] Ocultar credenciales de base de datos usando variables de entorno.
- [x] Refactorización y estandarización de código a `camelCase`.
- [x] Validación de conflictos de horarios en citas (solapamiento según duración del servicio).
- [ ] Validar que las citas sean en el futuro y dentro de horario laboral.
- [ ] Validar unicidad de email al actualizar usuario.
- [ ] Manejar eliminación de usuario/servicio con citas asociadas (soft delete).
- [ ] Agregar validaciones (`@Valid`) y DTOs a Servicio y Cita.
- [ ] Handler genérico de excepciones (evitar exponer stack traces).
- [ ] Perfiles de configuración separados (dev/prod).
- [ ] Implementar autenticación JWT y autorización por roles.
- [ ] Desarrollo del Frontend interactivo consumiendo esta API.

---
*Desarrollado por Eduardo Andrés Segovia Román.*