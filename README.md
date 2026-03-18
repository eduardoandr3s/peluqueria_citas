# ✂️ Peluquería "Lalo Segovia" - API REST

Este repositorio contiene el backend de un sistema integral de gestión de citas y catálogo para una peluquería. Desarrollado como una API REST robusta, este proyecto está orientado a facilitar la reserva de servicios, gestión de horarios y administración de clientes, separando la lógica del lado del cliente y del administrador.

## 🚀 Tecnologías Utilizadas

El núcleo del proyecto está construido bajo estándares actuales de la industria, garantizando un buen rendimiento y escalabilidad:

* **Java 21 (Temurin LTS)**
* **Spring Boot 4.0.3** (Framework principal)
* **PostgreSQL** (Base de datos relacional)
* **Spring Data JPA / Hibernate** (ORM para el mapeo de la base de datos)
* **Spring Boot Validation** (Validación estricta de datos de entrada)
* **Maven** (Gestor de dependencias y construcción)
* **Lombok** (Optimización y reducción de código *boilerplate*)

## ✨ Características Implementadas

* **Arquitectura Multicapa:** Separación clara entre Controladores (API), Servicios (Lógica de negocio) y Repositorios (Acceso a datos).
* **Patrón DTO (Data Transfer Object):** Implementado para la entidad `Usuario`, garantizando que información sensible (como las contraseñas) no se exponga en las respuestas JSON.
* **Manejo Global de Excepciones:** Uso de `@RestControllerAdvice` para capturar y estandarizar las respuestas de error (Ej: 404 para recursos no encontrados, 400 para errores de validación, 409 para conflictos de integridad en la base de datos).
* **Validación de Datos:** Uso de anotaciones como `@NotBlank` y `@Email` para proteger la integridad de los datos antes de llegar a la base de datos.
* **Tipado Estricto con Enums:** Blindaje del estado de las citas mediante enumeraciones (`PENDIENTE`, `CONFIRMADA`, `ANULADA`).

## 🗄️ Modelo de Datos

La arquitectura de la información se basa en un esquema relacional limpio con tres entidades principales:

* **`servicios`**: Catálogo de la peluquería (ej. cortes, tinturas, alisados). Almacena descripción, duración en minutos y precio.
* **`usuarios`**: Gestión de clientes registrados, almacenando datos de contacto y credenciales de acceso.
* **`citas`**: Entidad transaccional que vincula a un `usuario` con un `servicio` en una `fecha_hora` específica. Gestiona su estado mediante un `Enum` mapeado a texto.

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

3.  **Configurar credenciales:**
    Navega hasta el archivo `src/main/resources/application.properties` y modifica la contraseña por la de tu usuario de PostgreSQL local:
    ```properties
    spring.datasource.password=tu_contraseña_aqui
    ```
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
- [ ] Encriptación de contraseñas (BCrypt) e implementación de Spring Security.
- [ ] Ocultar credenciales de base de datos usando variables de entorno.
- [ ] Implementación de lógicas de negocio (validación de solapamiento de horas en citas).
- [ ] Desarrollo del Frontend interactivo consumiendo esta API.

---
*Desarrollado por Eduardo Andrés Segovia Román.*