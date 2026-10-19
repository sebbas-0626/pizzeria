package com.pizzeria;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba lo mínimo que la colección de Postman tiene que cumplir para no quedarse obsoleta en silencio.
 *
 * <p><strong>Por qué pruebas sobre un JSON y no pruebas de la API.</strong> La colección sirve para probar a
 * mano, y es lo primero que se queda viejo: nadie se entera de que a una petición le falta un caso de error
 * hasta que alguien la ejecuta y ve algo que no es de la API. Estas pruebas comprueban lo que se puede
 * comprobar sin levantar nada: que sea importable, que ninguna URL lleve el host escrito a mano, que toda
 * variable usada esté declarada y que **todo endpoint ya implementado tenga su petición**.
 *
 * <p>La última es la que ata la colección al desarrollo: en cuanto una operación pasa a {@code Hecho} en
 * {@code api-contract.md}, tiene que existir una petición para ella. Es una versión barata de lo que hará
 * US-045 (contrastar el OpenAPI generado con el contrato congelado), y evita la parte de aquel trabajo que
 * depende de que alguien se acuerde.
 *
 * <p>No se comprueba el contenido de los ejemplos guardados: son documentación, no contrato, y un ejemplo
 * desactualizado no hace fallar a nadie.
 */
class ColeccionPostmanTest {

    private static final Path RAIZ = Path.of("..");
    private static final Path COLECCION = RAIZ.resolve("api/bruno/pizzeria.postman_collection.json");
    private static final Path CONTRATO = RAIZ.resolve("docs/architecture/api-contract.md");

    /** Fila de la tabla de operaciones del contrato: método, ruta y la marca de que ya está hecha. */
    private static final Pattern OPERACION = Pattern.compile(
            "^\\|\\s*`(GET|POST|PUT|PATCH|DELETE)`\\s*\\|\\s*`(/api/v1[^`]*)`.*\u2705",
            Pattern.MULTILINE);

    /** Variable de la colección en una URL o en una cabecera: {@code {{baseUrl}}}. */
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{([^}]+)}}");

    private static final ObjectMapper LECTOR = new ObjectMapper();

    @Test
    @DisplayName("La colección es un JSON de Postman v2.1 con nombre y esquema")
    void laColeccionEsPostmanV21() throws Exception {
        JsonNode raiz = coleccion();

        assertThat(raiz.path("info").path("name").asText())
                .as("la colección tiene nombre")
                .isNotBlank();
        assertThat(raiz.path("info").path("schema").asText())
                .as("declara el esquema de Postman v2.1, que es lo que permite importarla")
                .endsWith("v2.1.0/collection.json");
        assertThat(peticiones(raiz))
                .as("la colección tiene peticiones")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Ninguna URL escribe el host y toda variable usada está declarada")
    void lasUrlsPasanPorBaseUrlConVariablesDeclaradas() throws Exception {
        JsonNode raiz = coleccion();
        Set<String> declaradas = new LinkedHashSet<>();
        for (JsonNode variable : raiz.path("variable")) {
            declaradas.add(variable.path("key").asText());
        }

        assertThat(declaradas).as("la colección declara variables").contains("baseUrl");

        List<String> urls = new ArrayList<>();
        for (JsonNode peticion : peticiones(raiz)) {
            urls.add(peticion.path("request").path("url").path("raw").asText());
        }

        // Un `localhost` escrito a mano hace que la colección sólo funcione en la máquina de quien la
        // escribió, y en un equipo nuevo el fallo es un error de conexión que no dice nada del backend.
        assertThat(urls)
                .as("toda URL empieza por {{baseUrl}}")
                .isNotEmpty()
                .allSatisfy(url -> assertThat(url).startsWith("{{baseUrl}}"));

        // Y una variable sin declarar también: Postman la deja vacía sin avisar, y el fallo sale en la
        // petición ajena, nunca en la que escribió el nombre mal.
        List<String> usadas = new ArrayList<>();
        for (String url : urls) {
            Matcher variables = VARIABLE.matcher(url);
            while (variables.find()) {
                usadas.add(variables.group(1));
            }
        }
        assertThat(usadas)
                .as("ninguna URL usa una variable sin declarar en la colección")
                .isNotEmpty()
                .allSatisfy(nombre -> assertThat(declaradas).contains(nombre));
    }

    @Test
    @DisplayName("Todo endpoint ya implementado tiene su petición")
    void todoEndpointImplementadoTieneSuPeticion() throws Exception {
        Set<String> rutas = new LinkedHashSet<>();
        Matcher operaciones = OPERACION.matcher(Files.readString(CONTRATO));
        while (operaciones.find()) {
            rutas.add(operaciones.group(2));
        }

        assertThat(rutas)
                .as("api-contract.md marca al menos un endpoint como Hecho; si no, la comprobación no "
                        + "comprueba nada y hay que avisar de que el contrato se ha reescrito")
                .isNotEmpty();

        List<String> pedidas = new ArrayList<>();
        for (JsonNode peticion : peticiones(coleccion())) {
            pedidas.add(rutaDe(peticion.path("request").path("url").path("raw").asText()));
        }

        List<String> sinPeticion = new ArrayList<>();
        for (String ruta : rutas) {
            boolean cubierta = pedidas.stream().anyMatch(pedida -> coincide(ruta, pedida));
            if (!cubierta) {
                sinPeticion.add(ruta);
            }
        }

        assertThat(sinPeticion)
                .as("estos endpoints están Hechos en api-contract.md y no tienen petición en la colección: "
                        + "añádela a la carpeta de su recurso")
                .isEmpty();
    }

    private static List<JsonNode> peticiones(JsonNode raiz) {
        List<JsonNode> encontradas = new ArrayList<>();
        for (JsonNode carpeta : raiz.path("item")) {
            for (JsonNode peticion : carpeta.path("item")) {
                encontradas.add(peticion);
            }
        }
        return encontradas;
    }

    /** Ruta de una URL de la colección, sin la base ni los parámetros de consulta. */
    private static String rutaDe(String url) {
        String sinBase = url.replace("{{baseUrl}}", "");
        int interrogacion = sinBase.indexOf('?');
        return interrogacion >= 0 ? sinBase.substring(0, interrogacion) : sinBase;
    }

    /**
     * Una ruta del contrato con {@code {parametro}} en un segmento casa con cualquier valor de ese
     * segmento en la colección, que es donde va una variable como {@code {{pizzaIdMargarita}}}.
     */
    private static boolean coincide(String rutaContrato, String rutaPeticion) {
        String[] contractuales = rutaContrato.split("/");
        String[] pedidas = rutaPeticion.split("/");
        if (contractuales.length != pedidas.length) {
            return false;
        }
        for (int i = 0; i < contractuales.length; i++) {
            String segmento = contractuales[i];
            boolean esParametro = segmento.startsWith("{") && segmento.endsWith("}");
            if (!esParametro && !segmento.equals(pedidas[i])) {
                return false;
            }
        }
        return true;
    }

    private static JsonNode coleccion() throws Exception {
        assertThat(Files.exists(COLECCION))
                .as("existe %s (si no aparece, la colección está en otro sitio o se ha borrado)", COLECCION)
                .isTrue();
        return LECTOR.readTree(COLECCION.toFile());
    }
}