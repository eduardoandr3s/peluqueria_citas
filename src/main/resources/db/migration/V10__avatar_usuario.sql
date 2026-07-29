-- V10: Foto de perfil del usuario.
-- Igual que en servicios (V9), se guarda la CLAVE del objeto en el almacen y no la
-- URL: aqui es aun mas importante, porque el bucket de avatares es privado y la URL
-- se firma al leer, asi que caduca y no tendria sentido persistirla.
ALTER TABLE usuarios
    ADD COLUMN avatar_clave VARCHAR(255);
