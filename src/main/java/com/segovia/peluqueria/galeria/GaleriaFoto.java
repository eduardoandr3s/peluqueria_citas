package com.segovia.peluqueria.galeria;

import com.segovia.peluqueria.usuario.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Una foto de la galeria de trabajos.
 *
 * <p>Guarda dos claves del almacen y ninguna URL: la grande, que solo se pide al
 * abrir una foto, y la miniatura, que es lo unico que carga la rejilla. Separarlas
 * no es cosmetico: el limite del plan gratuito de Storage es el trafico, y una
 * rejilla servida con las imagenes grandes lo multiplica por diez.
 *
 * <p>El dueno es opcional y sin dueno significa "del negocio": las fotos que ya
 * existian las subio un administrador antes de que esto se guardara, y solo un
 * administrador las toca. No se les invento un dueno con un backfill, porque eso
 * le daria a una persona el derecho a borrar material de la peluqueria.
 *
 * <p>La miniatura es opcional a proposito. Se genera en el cliente (que es donde
 * ya se redimensiona todo lo demas, para no gastar los 0,1 CPU de produccion), asi
 * que un cliente que no la mande deja la columna a null y al leer se cae a la
 * imagen grande. Se degrada en trafico, nunca en un hueco vacio.
 */
@Getter
@Setter
@Entity
@Table(name = "galeria_fotos")
public class GaleriaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_foto")
    private Integer idFoto;

    @Column(name = "imagen_clave", nullable = false, length = 255)
    private String imagenClave;

    @Column(name = "miniatura_clave", length = 255)
    private String miniaturaClave;

    @Column(length = 120)
    private String titulo;

    /** Orden manual en la rejilla. Empata por id para que el listado sea estable. */
    @Column(nullable = false)
    private Integer orden = 0;

    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida = LocalDateTime.now();

    /** Quien la subio. Null = del negocio, ver la nota de arriba. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subido_por")
    private Usuario subidoPor;
}
