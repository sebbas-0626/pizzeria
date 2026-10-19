package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el perfil local apunta al PostgreSQL del contenedor y a ningún otro.
 *
 * <p><strong>La decisión que esta prueba defiende.</strong> El proyecto usa una sola base de datos en
 * desarrollo: la del contenedor de Docker, en la red de Compose, donde se llama {@code db} y su puerto no
 * se publica en el host. Si en la máquina hay además un PostgreSQL instalado, ese otro <strong>no</strong> se
 * usa nunca.
 *
 * <p><strong>Por qué hace falta, y no es una formalidad.</strong> El perfil local tenía
 * {@code ${POSTGRES_HOST:localhost}} como valor por defecto. Con él, arrancar la aplicación fuera de
 * Docker —un {@code mvnw spring-boot:run} a mano, que es lo primero que se prueba cuando algo falla— caía
 * en {@code localhost:5432} sin avisar. En una máquina con PostgreSQL local eso significa conectarse al
 * servidor equivocado, y el error que sale es {@code FATAL: la autenticación password falló}, que parece un
 * problema de credenciales y no lo es. Perder una hora diagnosticando una contraseña que estaba bien es
 * exactamente el tipo de fallo que esta prueba evita.
 *
 * <p>Se comprueba el <strong>texto</strong> de la configuración y no su comportamiento porque el CI corre en
 * {@code ubuntu-latest} y no hay allí ningún PostgreSQL al que conectarse. Lo que hay que fijar es que el
 * valor por defecto no vuelva.
 */
class LocalProfileConfigTest {

    private static final Path PERFIL_LOCAL = Path.of("src/main/resources/application-local.yml");

    @Test
    @DisplayName("El perfil local no tiene valor por defecto para el host de la base de datos")
    void elPerfilLocalNoTieneHostPorDefecto() throws Exception {
        // Se lee la configuración SIN comentarios. El archivo explica por qué se quitó el valor por
        // defecto, y esa explicación menciona el texto viejo: comprobar el archivo entero daría un
        // falso positivo en la propia documentación de la decisión.
        String configuracion = sinComentarios();

        // `${POSTGRES_HOST:localhost}` es exactamente lo que se quitó. Con el host presente y sin
        // defecto, arrancar fuera del laboratorio falla diciendo qué variable falta, en vez de
        // conectarse a otra base de datos sin avisar.
        assertThat(configuracion)
                .as("el host de la base de datos no puede tener valor por defecto")
                .doesNotContain("${POSTGRES_HOST:")
                .doesNotContain("${POSTGRES_PORT:");
    }

    @Test
    @DisplayName("El perfil local no inventa las credenciales")
    void elPerfilLocalNoInventaPuertoNiCredenciales() throws Exception {
        String configuracion = sinComentarios();

        // Sin valor por defecto: si falta la variable, el error la nombra en vez de dejar arrancar
        // con una contraseña inventada.
        assertThat(configuracion).contains("username: ${POSTGRES_USER}");
        assertThat(configuracion).contains("password: ${POSTGRES_PASSWORD}");
    }

    @Test
    @DisplayName("La url del perfil local exige host y puerto de la variable de entorno")
    void laUrlExigeHostYPuerto() throws Exception {
        String configuracion = sinComentarios();

        assertThat(configuracion).contains("jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/");
    }

    @Test
    @DisplayName("El perfil local es YAML valido y declara el datasource")
    void elPerfilLocalEsValido() throws Exception {
        Map<String, Object> configuracion = leer();

        @SuppressWarnings("unchecked")
        Map<String, Object> spring = (Map<String, Object>) configuracion.get("spring");
        assertThat(spring).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> datasource = (Map<String, Object>) spring.get("datasource");
        assertThat(datasource).as("el perfil local declara el datasource").isNotNull();

        String url = (String) datasource.get("url");
        assertThat(url).isNotNull().contains("jdbc:postgresql://").contains("${POSTGRES_HOST}");
    }

    @Test
    @DisplayName("El compose le pasa a la aplicacion el nombre del contenedor, no localhost")
    void elComposeApuntaAlNombreDelContenedor() throws Exception {
        // La otra mitad del contrato: si el perfil pide `${POSTGRES_HOST}` sin defecto, el compose
        // tiene que inyectar el nombre del servicio. Si dejara de hacerlo, el laboratorio no
        // arrancaría —que es el fallo ruidoso que se quiere—, pero conviene que la prueba lo diga.
        String compose = Files.readString(Path.of("../infra/local/docker-compose.yml"));

        assertThat(compose).contains("POSTGRES_HOST: db");
        assertThat(compose).contains("POSTGRES_PORT: \"5432\"");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leer() throws Exception {
        try (InputStream entrada = Files.newInputStream(PERFIL_LOCAL)) {
            Map<String, Object> mapa = new Yaml().load(entrada);
            assertThat(mapa).as("el perfil local no está vacío").isNotNull();
            return mapa;
        }
    }

    /**
     * El archivo de configuración sin sus líneas de comentario.
     *
     * <p>Hace falta porque el perfil explica <em>por qué</em> no hay valor por defecto, y esa
     * explicación cita el texto que se quitó. Comprobar el archivo entero daría un falso positivo en la
     * propia documentación de la decisión, que es justo el tipo de prueba que luego nadie se cree.
     */
    private static String sinComentarios() throws Exception {
        return Files.readAllLines(PERFIL_LOCAL).stream()
                .filter(linea -> !linea.strip().startsWith("#"))
                .reduce("", (a, b) -> a + b + "\n");
    }
}