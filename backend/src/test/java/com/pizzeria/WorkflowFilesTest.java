package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que los ficheros de GitHub Actions y Dependabot son YAML válido y tienen la forma que
 * GitHub exige.
 *
 * <p>No es una prueba negativa ni una formalidad. Un {@code .yml} mal formado en
 * {@code .github/workflows} **no da error visible**: GitHub ignora el workflow y no se ejecuta.
 * Un pipeline que no se ejecuta parece un pipeline que pasa, y eso es peor que un pipeline roto,
 * porque se descubre cuando ya se ha roto algo de verdad.
 *
 * <p>Se valida con el mismo parser que usa Spring para sus ficheros de configuración, así que no
 * hace falta ninguna dependencia nueva en el repositorio.
 */
class WorkflowFilesTest {

    private static final Path RAIZ = Path.of("..");

    @Test
    @DisplayName("Los workflows son YAML valido y declaran cuando se disparan")
    void losWorkflowsSonValidos() throws Exception {
        for (String nombre : List.of("ci.yml", "ci-integration.yml")) {
            Path workflow = RAIZ.resolve(".github/workflows").resolve(nombre);
            assertThat(Files.exists(workflow)).as("existe %s", workflow).isTrue();

            Map<String, Object> contenido = leer(workflow);

            assertThat(contenido).as("%s tiene nombre", nombre).containsKey("name");
            assertThat(contenido).as("%s tiene jobs", nombre).containsKey("jobs");

            // Trampa del YAML 1.1: la clave `on:` se resuelve como el booleano `true`, así que
            // SnakeYAML devuelve un mapa con clave `true` y no con clave `"on"`. GitHub lo
            // interpreta bien porque usa semántica YAML 1.2, pero cualquier validador que use
            // YAML 1.1 ve otra cosa. Por eso se aceptan las dos formas.
            boolean declaraDisparador = contenido.containsKey("on") || contenido.containsKey(Boolean.TRUE);
            assertThat(declaraDisparador).as("%s declara on:", nombre).isTrue();
        }
    }

    @Test
    @DisplayName("dependabot.yml tiene la forma que Dependabot espera")
    void dependabotEsValido() throws Exception {
        Map<String, Object> contenido = leer(RAIZ.resolve(".github/dependabot.yml"));

        assertThat(contenido).containsKeys("version", "updates");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actualizaciones = (List<Map<String, Object>>) contenido.get("updates");

        // Maven, Docker y las acciones del propio pipeline. Un ecosistema que se olvide aquí
        // deja de actualizarse en silencio.
        assertThat(actualizaciones).extracting(ecosistema -> ecosistema.get("package-ecosystem"))
                .containsExactlyInAnyOrder("maven", "docker", "github-actions");
    }

    @Test
    @DisplayName("El pipeline principal verifica con JDK 21, no con el JDK por defecto del runner")
    void elPipelineFijaElJdk21() throws Exception {
        Map<String, Object> contenido = leer(RAIZ.resolve(".github/workflows/ci.yml"));
        String yaml = Files.readString(RAIZ.resolve(".github/workflows/ci.yml"));

        // El Enforcer ya rechazaría un JDK antiguo, pero compilar con uno y probar con otro es
        // exactamente la divergencia que ADR-0010 quiere evitar.
        assertThat(yaml).contains("java-version: '21'");
        assertThat(yaml).contains("distribution: temurin");
        assertThat(yaml).contains("cache: maven");

        // Y debe ejecutar la fase completa, no sólo `test`: las pruebas de integración viven en
        // `verify` (Failsafe) y `test` no las lanza.
        assertThat(yaml).contains("./mvnw -B -ntp verify");
        assertThat(contenido).containsKey("jobs");
    }

    @Test
    @DisplayName("El pipeline tiene los tres jobs: verificar, secretos y compose")
    void elPipelineTieneLosTresJobs() throws Exception {
        Map<String, Object> contenido = leer(RAIZ.resolve(".github/workflows/ci.yml"));

        @SuppressWarnings("unchecked")
        Map<String, Object> jobs = (Map<String, Object>) contenido.get("jobs");

        assertThat(jobs).containsOnlyKeys("verify", "secrets", "compose");
    }

    @Test
    @DisplayName("El escaneo de secretos recorre el historial completo")
    void elEscaneoDeSecretosRecorreElHistorial() throws Exception {
        String yaml = Files.readString(RAIZ.resolve(".github/workflows/ci.yml"));

        // Sin fetch-depth 0, gitleaks sólo ve el commit actual: un secreto mergeado la semana
        // pasada pasaría el escaneo sin que nadie se entere.
        assertThat(yaml).contains("gitleaks");
        assertThat(yaml).contains("fetch-depth: 0");
    }

    @Test
    @DisplayName("El pipeline no usa secretos propios: un fork podria leerlos")
    void elPipelineNoUsaSecretosPropios() throws Exception {
        String yaml = Files.readString(RAIZ.resolve(".github/workflows/ci.yml"));

        // `GITHUB_TOKEN` lo emite GitHub para cada ejecución y no se expone al código del pull
        // request, así que su uso es legítimo. Cualquier OTRO secrets.* sí sería legible desde
        // un fork, porque los workflows de `pull_request` se ejecutan con el repositorio del que
        // viene el PR. Así el escaneo de secretos funciona sin configurar nada.
        Set<String> referenciados = Pattern.compile("secrets\\.([A-Z_]+)")
                .matcher(yaml)
                .results()
                .map(resultado -> resultado.group(1))
                .collect(Collectors.toSet());

        assertThat(referenciados)
                .as("el único secreto permitido es GITHUB_TOKEN, que emite GitHub por ejecución")
                .isSubsetOf("GITHUB_TOKEN");
    }

    @Test
    @DisplayName("Los workflows se disparan en pull_request, que es donde llegan los forks")
    void losWorkflowsCubrenPullRequest() throws Exception {
        for (String fichero : List.of("ci.yml", "ci-integration.yml")) {
            String yaml = Files.readString(RAIZ.resolve(".github/workflows").resolve(fichero));
            assertThat(yaml).as("%s cubre pull_request", fichero).contains("pull_request");
        }
    }

    private Map<String, Object> leer(Path fichero) throws Exception {
        try (InputStream entrada = Files.newInputStream(fichero)) {
            Map<String, Object> mapa = new Yaml().load(entrada);
            assertThat(mapa).as("%s no está vacío", fichero).isNotNull();
            return mapa;
        }
    }
}