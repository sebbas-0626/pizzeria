package com.pizzeria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba lo que los scripts del laboratorio local tienen que seguir diciendo.
 *
 * <p><strong>Por qué pruebas sobre texto de un PowerShell y no pruebas del PowerShell.</strong> El CI corre
 * en `ubuntu-latest` (ver {@code ci.yml}), así que no se puede ejecutar un {@code .ps1} allí. Lo que sí
 * importa y se puede verificar en cualquier sistema operativo es que los scripts <em>declaran</em> lo que
 * sepromete: las acciones de {@code db.ps1}, que las dos acciones destructivas exigen confirmación, y que
 * los dos scripts de arranque y parada no han vuelto a confiar ciegamente en {@code $LASTEXITCODE}.
 *
 * <p><strong>Por qué importa la confirmación de las acciones destructivas.</strong> El 2026-10-17
 * {@code reset} sin {@code -Force} imprimía «Reset cancelado» y <strong>destruía el volumen igualmente</strong>:
 * la comprobación estaba dentro del {@code switch} y usaba {@code Write-Error}, que con
 * {@code $ErrorActionPreference = 'Continue'} escribe el error pero no detiene la ejecución. Un mensaje que
 * dice «cancelado» y luego borra es peor que no tener ninguno.
 *
 * <p>El segundo fallo que estas pruebas cubren es más silencioso: con PowerShell 5.1,
 * {@code $ErrorActionPreference = 'Stop'} convierte en error terminante cualquier salida por
 * <em>stderr</em> de un comando nativo. {@code docker info} y {@code docker compose} escriben avisos ahí en
 * situaciones normales, así que los scripts abortaban antes de hacer su trabajo.
 */
class LocalScriptsTest {

    private static final Path RAIZ = Path.of("..");
    private static final Path SCRIPTS = RAIZ.resolve("scripts");

    @Test
    @DisplayName("db.ps1 declara las cinco acciones de gestion de datos")
    void dbDeclaraLasCincoAcciones() throws Exception {
        String script = leer("db.ps1");

        // Si una acción desaparece del ValidateSet, deja de existir: es el contrato del script.
        Pattern conjunto = Pattern.compile("ValidateSet\\(([^)]*)\\)");
        var coincidencia = conjunto.matcher(script);

        assertThat(coincidencia.find()).as("db.ps1 declara un ValidateSet").isTrue();
        assertThat(coincidencia.group(1))
                .as("las acciones de db.ps1")
                .contains("'shell'")
                .contains("'seed'")
                .contains("'backup'")
                .contains("'restore'")
                .contains("'reset'");
    }

    @Test
    @DisplayName("Las acciones destructivas de db.ps1 exigen -Force")
    void lasAccionesDestructivasExigenForce() throws Exception {
        String script = leer("db.ps1");

        // La comprobación tiene que estar FUERA del switch y lanzar, no avisar. Con `-not $Force` y
        // `throw` antes de la acción, `reset` y `restore` se niegan y no tocan nada.
        assertThat(script).contains("[switch]$Force");

        int indiceReset = script.indexOf("-eq 'reset' -and -not $Force");
        int indiceRestore = script.indexOf("-eq 'restore' -and -not $Force");
        int indiceSwitch = script.indexOf("switch ($Accion)");

        assertThat(indiceReset).as("reset comprueba -Force").isGreaterThan(-1);
        assertThat(indiceRestore).as("restore comprueba -Force").isGreaterThan(-1);
        // Si la comprobación estuviera dentro del switch, llegaría tarde: la acción ya se habría ejecutado.
        assertThat(indiceReset).as("la comprobación de reset va antes del switch").isLessThan(indiceSwitch);
        assertThat(indiceRestore).as("la comprobación de restore va antes del switch").isLessThan(indiceSwitch);

        // Y lanzar, no escribir: `throw` detiene, `Write-Error` con 'Continue' no.
        assertThat(script).contains("throw 'Reset cancelado");
        assertThat(script).contains("throw 'Restore cancelado");
    }

    @Test
    @DisplayName("Los scripts de entorno no deciden el exito solo con $LASTEXITCODE tras una llamada a Docker")
    void losScriptsBlindanLasLlamadasNativas() throws Exception {
        for (String nombre : List.of("dev-up.ps1", "dev-down.ps1", "db.ps1")) {
            // PowerShell 5.1 aborta el script ante cualquier stderr de un comando nativo si la
            // preferencia es 'Stop'. Los tres tienen que restaurarla alrededor de las llamadas a
            // Docker, porque si no abortan con un aviso normal de Docker.
            assertThat(leer(nombre))
                    .as("%s restaura la preferencia de error alrededor de los nativos", nombre)
                    .contains("$preferenciaPrevia = $ErrorActionPreference")
                    .contains("$ErrorActionPreference = 'Continue'");
        }

        // `dev-up` y `dev-down` hacen una SEGUNDA llamada a Docker (`compose ps`) después de la que
        // decide, así que `$LASTEXITCODE` ya no vale al comprobarlo: hay que guardarlo antes.
        for (String nombre : List.of("dev-up.ps1", "dev-down.ps1")) {
            assertThat(leer(nombre))
                    .as("%s guarda el codigo de salida antes de seguir", nombre)
                    .contains("$codigo");
        }

        // `db.ps1` comprueba `$LASTEXITCODE` en la línea siguiente a cada llamada, sin ejecutar nada
        // en medio. Ahí es seguro, y es la forma correcta de escribirlo.
        assertThat(leer("db.ps1"))
                .as("db.ps1 comprueba cada salida de Docker en el acto")
                .contains("if ($LASTEXITCODE -ne 0) { throw");
    }

    @Test
    @DisplayName("Los scripts toman las credenciales de .env y no las escriben")
    void losScriptsNoEscribenCredenciales() throws Exception {
        for (String nombre : List.of("dev-up.ps1", "dev-down.ps1", "db.ps1")) {
            String script = leer(nombre);

            // Una contraseña escrita en un script versionado es una credencial en el repositorio.
            // db.ps1 la lee de .env; dev-up y dev-down no la necesitan para nada.
            assertThat(script)
                    .as("%s no contiene una contraseña escrita", nombre)
                    .doesNotContain("cambia_esta_contrasena_local");
        }

        assertThat(leer("db.ps1"))
                .as("db.ps1 lee POSTGRES_USER y POSTGRES_DB de .env")
                .contains("POSTGRES_USER")
                .contains("POSTGRES_DB");
    }

    @Test
    @DisplayName("db.ps1 no intenta sembrar si las migraciones no estan aplicadas")
    void dbNoSiembraSobreUnaBaseVacia() throws Exception {
        // El esquema lo crea Flyway al arrancar la APLICACIÓN, no este script. Con un volumen recién
        // recreado, sembrar falla con el `relation "pizzas" does not exist` de PostgreSQL: un mensaje
        // que habla de SQL y no dice que lo que falta es levantar la aplicación. Se comprueba antes.
        String script = leer("db.ps1");

        assertThat(script)
                .as("db.ps1 mira si la tabla products existe antes de sembrar")
                .contains("information_schema.tables")
                .contains("table_name = 'products'");

        // Y la comprobación va ANTES de cargar el fichero: mirarla después sólo cambiaría el momento
        // del error, no su causa.
        int indiceMira = script.indexOf("information_schema.tables");
        int indiceCarga = script.indexOf("Get-Content -LiteralPath $datosDev -Raw");
        assertThat(indiceMira).as("la comprobación está en el script").isGreaterThan(-1);
        assertThat(indiceCarga).as("la carga del fichero está en el script").isGreaterThan(-1);
        assertThat(indiceMira).as("se comprueba el esquema antes de sembrar")
                .isLessThan(indiceCarga);

        assertThat(script)
                .as("el mensaje dice que las migraciones no están aplicadas y cómo se soluciona")
                .contains("migraciones no estan aplicadas")
                .contains("dev-up.ps1 -Detach");
    }

    @Test
    @DisplayName("Los tres scripts existen")
    void losScriptsExisten() {
        assertThat(SCRIPTS.resolve("dev-up.ps1")).exists();
        assertThat(SCRIPTS.resolve("dev-down.ps1")).exists();
        assertThat(SCRIPTS.resolve("db.ps1")).exists();
    }

    private static String leer(String nombre) throws Exception {
        Path fichero = SCRIPTS.resolve(nombre);
        assertThat(Files.exists(fichero)).as("existe scripts/%s", nombre).isTrue();
        return Files.readString(fichero);
    }
}