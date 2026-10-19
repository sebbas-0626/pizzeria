package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que los ficheros de entorno del laboratorio local tienen el formato que Docker Compose
 * sabe leer.
 *
 * <p><strong>Por qué hace falta, y por qué no es una formalidad.</strong> El 2026-10-17 una línea ajena
 * se coló en {@code infra/local/.env} —parecía la salida de un comando de shell— y el laboratorio local
 * dejó de levantar entero. El síntoma es {@code key cannot contain a space}, que no dice qué línea es ni
 * por qué. Nada del repositorio escribe en ese archivo: está en {@code .gitignore} y sólo lo crea
 * {@code scripts/dev-up.ps1} a partir de la plantilla. Por eso esta prueba no puede evitar el problema,
 * pero sí convertir un fallo de Docker en un mensaje que dice el número de línea.
 *
 * <p>El formato es el de un fichero {@code .env} de Docker Compose:
 *
 * <ul>
 *   <li>líneas vacías y comentarios que empiezan por {@code #} se permiten;</li>
 *   <li>el resto debe ser {@code CLAVE=valor}, con {@code CLAVE} respetando
 *       {@code [A-Za-z_][A-Za-z0-9_]*}.</li>
 * </ul>
 *
 * <p><strong>Por qué el mensaje del fallo no imprime la línea.</strong> Porque un {@code .env} puede
 * contener una contraseña o un token, y un error de validación que lo vuelque en consola lo acaba
 * copiando alguien en un informe. Se reporta el número de línea y su longitud, que es lo justo para
 * encontrarla con `Select-String` o un editor.
 *
 * <p>{@code .env} está en {@code .gitignore}, así que en CI no existe y su comprobación se salta. La
 * plantilla sí se valida siempre, porque está versionada y es la que se copia.
 */
class LocalEnvFilesTest {

    private static final Path RAIZ = Path.of("..");

    private static final Path PLANTILLA = RAIZ.resolve("infra/local/.env.example");
    private static final Path ENTORNO = RAIZ.resolve("infra/local/.env");

    /** Una clave de entorno: letra o guion bajo primero, y sólo letras, dígitos y guiones bajos. */
    private static final Pattern CLAVE = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*=");

    @Test
    @DisplayName("La plantilla .env.example solo contiene lineas vacias, comentarios y CLAVE=valor")
    void laPlantillaTieneElFormatoCorrecto() throws Exception {
        // Se valida siempre: la plantilla está versionada y es la que `dev-up.ps1` copia. Una línea
        // corrupta aquí se propaga a cada máquina nueva que levante el laboratorio.
        List<String> offending = lineasInvalidas(PLANTILLA);

        assertThat(offending)
                .as("%s tiene lineas que Docker Compose no puede leer: %s", PLANTILLA.getFileName(), offending)
                .isEmpty();
    }

    @Test
    @DisplayName("La plantilla declara las cuatro variables que el compose exige")
    void laPlantillaDeclaraLasVariablesNecesarias() throws Exception {
        // El compose las pide con `${VAR:?mensaje}`, así que si falta una el laboratorio no levanta.
        // Comprobarlo aquí da un mensaje que dice qué falta, en vez de un error de interpolación.
        String plantilla = Files.readString(PLANTILLA);

        assertThat(plantilla).contains("POSTGRES_DB=");
        assertThat(plantilla).contains("POSTGRES_USER=");
        assertThat(plantilla).contains("POSTGRES_PASSWORD=");
        assertThat(plantilla).contains("APP_PORT=");
    }

    @Test
    @DisplayName("La plantilla no contiene ningun valor con pinta de credencial real")
    void laPlantillaNoTieneCredencialesReales() throws Exception {
        String plantilla = Files.readString(PLANTILLA);

        // Los valores de la plantilla son de mentira y se versionan. Si alguien pega ahí una contraseña
        // de verdad, esta prueba falla antes de que llegue a un commit. Es la defensa barata: la
        // seria es el escaneo de secretos del pipeline.
        assertThat(plantilla)
                .as("no debe aparecer un token de sesion en la plantilla")
                .doesNotContain("ses_")
                .doesNotContain("sk-")
                .doesNotContain("ghp_")
                .doesNotContain("AKIA");
    }

    @Test
    @DisplayName("El .env local, si existe, solo contiene lineas vacias, comentarios y CLAVE=valor")
    void elEntornoLocalTieneElFormatoCorrecto() throws Exception {
        // No se salta en silencio: si el fichero existe y está corrupto, el laboratorio no levanta y
        // la causa es invisible. Si no existe, `dev-up.ps1` lo crea y no hay nada que validar.
        if (!Files.exists(ENTORNO)) {
            return;
        }

        List<String> offending = lineasInvalidas(ENTORNO);

        assertThat(offending)
                .as("""
                        %s tiene lineas que Docker Compose no puede leer: %s
                        Docker compose responderia 'key cannot contain a space', que no dice que linea es.
                        Revisa esas lineas y borralas, o regenera el archivo:
                            Copy-Item infra/local/.env.example infra/local/.env
                        """, ENTORNO.getFileName(), offending)
                .isEmpty();
    }

    @Test
    @DisplayName("La plantilla termina en salto de linea, para que anadir algo no rompa la ultima variable")
    void laPlantillaTerminaEnSaltoDeLinea() throws Exception {
        // Sin salto de línea final, lo que se añada al final se pega a la última variable:
        // `APP_PORT=8080` + un comando se convierte en `APP_PORT=8080opencode -s ses_...`. Docker
        // Compose lo lee como una variable válida llamada `APP_PORT8080opencode...` con un valor
        // raro, sin dar ningún error: el laboratorio levantaría con el puerto equivocado y nadie
        // sabría por qué. Con el salto de línea, la línea basura se detecta sola.
        assertThat(ultimoByte(PLANTILLA))
                .as("%s debe terminar en LF (0x0A) o CRLF (0x0D)", PLANTILLA.getFileName())
                .isIn((byte) 0x0A, (byte) 0x0D);
    }

    /**
     * Números de línea de las que Compose no puede leer, con la longitud de la línea.
     *
     * <p>Se reporta el número y la longitud, nunca el contenido: el fichero puede contener una
     * contraseña y un mensaje de error no es el sitio donde publicarla.
     */
    private static List<String> lineasInvalidas(Path fichero) throws Exception {
        List<String> offending = new java.util.ArrayList<>();

        List<String> lineas = Files.readAllLines(fichero);
        for (int indice = 0; indice < lineas.size(); indice++) {
            String linea = lineas.get(indice);
            String sinEspacios = linea.strip();

            if (sinEspacios.isEmpty() || sinEspacios.startsWith("#")) {
                continue;
            }
            if (!CLAVE.matcher(sinEspacios).find()) {
                offending.add("linea " + (indice + 1) + " (" + linea.length() + " caracteres)");
            }
        }
        return offending;
    }

    private static byte ultimoByte(Path fichero) throws Exception {
        byte[] bytes = Files.readAllBytes(fichero);
        assertThat(bytes).as("%s no está vacío", fichero.getFileName()).isNotEmpty();
        return bytes[bytes.length - 1];
    }
}