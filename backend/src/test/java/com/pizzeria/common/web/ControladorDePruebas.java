package com.pizzeria.common.web;

import com.pizzeria.common.domain.DomainException;
import com.pizzeria.common.domain.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador que existe sólo para poder probar el manejador de errores.
 *
 * <p>No hay forma de probar un {@code @RestControllerAdvice} sin una ruta que lance cada excepción. Y
 * no se puede usar un endpoint real para eso porque todavía no existe ninguno: US-004 va antes que
 * US-010. Este controlador está en {@code src/test}, no en {@code src/main}: no se despliega.
 *
 * <p>Cada ruta lanza una excepción distinta para poder comprobar su traducción aislada.
 */
@RestController
@RequestMapping("/api/v1/_pruebas")
class ControladorDePruebas {

    @PostMapping("/validacion")
    void fallaLaValidacionDelCuerpo(@Valid @RequestBody PeticionDePrueba peticion) {
        // No hace nada: la validación falla antes de llegar aquí. El cuerpo es un record, así que
        // un JSON mal formado falla todavía antes, al deserializar.
    }

    @GetMapping("/dominio")
    void fallaElDominio(@RequestParam ErrorCode codigo) {
        throw new ExcepcionDePrueba(codigo, "Regla de negocio incumplida");
    }

    @GetMapping("/inesperado")
    void fallaDeFormaInesperada() {
        throw new IllegalStateException("Detalle interno que NO debe salir en la respuesta");
    }

    /**
     * Excepción de negocio de mentira, sólo para las pruebas.
     *
     * <p>Las excepciones reales las crea cada módulo en su historia: {@code OrderNotFoundException}
     * viene con US-032, no aquí. Esta existe para comprobar que el manejador traduce <em>cualquier</em>
     * {@link DomainException}, que es justo lo que se está probando.
     */
    static final class ExcepcionDePrueba extends DomainException {
        ExcepcionDePrueba(ErrorCode code, String message) {
            super(code, message);
        }
    }

    record PeticionDePrueba(@NotBlank String nombre, @Min(1) @Max(20) int cantidad) {
    }
}