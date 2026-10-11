package com.pizzeria.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Define lo que Swagger no puede deducir del código.
 *
 * <p>springdoc genera el contrato a partir de las clases, los métodos y sus anotaciones. Eso basta
 * para saber qué rutas existen y qué cuerpos se aceptan, pero hay tres cosas que el código no dice y
 * que quien integra necesita:
 *
 * <ol>
 *   <li><strong>Que la API habla Bearer y no Basic ni cookies.</strong> Sin esto, el botón
 *       <em>Authorize</em> de Swagger no aparece y documentar la autenticación se queda en prosa.</li>
 *   <li><strong>Cómo se llama y para qué sirve la API.</strong></li>
 *   <li><strong>Que hay una base estable de errores.</strong></li>
 * </ol>
 *
 * <p><strong>El esquema se declara pero no se aplica a todas las operaciones.</strong> En esta API los
 * endpoints públicos no llevan autenticación: {@code GET /api/v1/pizzas} es público y
 * {@code POST /api/v1/admin/pizzas} exige {@code ADMIN} (RN-SE-02). Aplicar el requisito de seguridad
 * globalmente marcaría como privados endpoints que no lo son, y el que integra acabaría enviando un
 * token donde no hace falta.
 *
 * <p>Por eso cada operación declara su propio requisito, con {@code @SecurityRequirement}, en la
 * historia que la crea. Aquí sólo se publica el <em>esquema</em> para que exista y sea utilizable.
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad. Es la clave que aparece en el botón Authorize de Swagger. */
    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    public OpenAPI contratoDeLaApi() {
        return new OpenAPI()
                .info(contratoInformacion())
                .components(componentes());
    }

    private Info contratoInformacion() {
        return new Info()
                .title("API de la pizzería")
                .version("v1")
                .description("""

                        Backend de una pizzería de una sola sede: catálogo de pizzas, pedidos **para
                        recogida presencial** y pago **en efectivo**.

                        No hay envíos a domicilio ni pasarelas de pago: son decisiones de alcance, no
                        ausencias.

                        ### Convenciones

                        - Todos los endpoints cuelgan de `/api/v1`.
                        - Los importes son **pesos colombianos (COP)**, enteros, sin decimales.
                        - **El servidor calcula todos los importes.** Si una petición incluye `price`,
                          `unitPrice` o `total`, esos campos se ignoran.
                        - Los errores salen como `application/problem+json` (RFC 9457) con un `code`
                          estable. Hay que comparar el `code`, no el mensaje.
                        - Un recurso ajeno responde **404**, no 403, para no revelar que existe.
                        """)
                .license(new License().name("Uso interno"));
    }

    private Components componentes() {
        return new Components().addSecuritySchemes(ESQUEMA_BEARER, esquemaBearer());
    }

    private SecurityScheme esquemaBearer() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""

                        Token JWT firmado con **RS256**, con una validez de 15 minutos.

                        - No hay token de renovación: al caducar hay que volver a iniciar sesión.
                        - El `sub` es el UUID del cliente y `roles` lleva `CUSTOMER` o `ADMIN`.
                        - No existe ninguna forma pública de obtener el rol `ADMIN` (RN-SE-03).
                        - Para probarlo desde Swagger: botón **Authorize**, pegar el token y ejecutar
                          una operación protegida.
                        """);
    }
}