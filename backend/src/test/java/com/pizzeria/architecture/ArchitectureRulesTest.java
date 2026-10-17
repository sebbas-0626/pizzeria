package com.pizzeria.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica las reglas de dependencia entre capas definidas en
 * {@code docs/development/spring-conventions.md} §1.
 *
 * <p>Una regla de arquitectura que sólo está escrita en un documento se rompe sin avisar.
 * Estas pruebas la convierten en un fallo de build.
 *
 * <p>Dirección de dependencias, de fuera hacia dentro ({@link #elDominioNoDependeDeFrameworks()}):
 *
 * <pre>
 *   infrastructure ──► application ──► domain
 *     (conoce a todos)   (orquesta)     (no conoce a nadie)
 * </pre>
 *
 * <p>Se usa el módulo {@code core} de ArchUnit y no {@code archunit-junit5}: las reglas se
 * ejecutan desde tests JUnit normales, sin atar el proyecto al motor de ArchUnit ni a su
 * compatibilidad con la versión de JUnit que fije Spring Boot.
 */
class ArchitectureRulesTest {

    private static final String BASE = "com.pizzeria";

    /** Prefijo de todo lo que cuelga de {@code com.pizzeria}. */
    private static final String P = BASE + "..";

    /**
     * Los únicos paquetes de primer nivel permitidos bajo {@code com.pizzeria}.
     *
     * <p>Una estructura de paquetes no se rompe de golpe: se erosiona. Alguien crea
     * {@code orders.domain} «sólo para esta historia», o {@code utils}, y las reglas de capas dejan de
     * encontrar el código nuevo porque viven en otra raíz. Fijar la lista aquí convierte esa erosión
     * en un fallo de build inmediato.
     */
    private static final Set<String> PAQUETES_DE_PRIMER_NIVEL =
            Set.of("application", "domain", "infrastructure", "shared");

    private static JavaClasses classes;

    @BeforeAll
    static void importarClases() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    @DisplayName("El dominio sólo depende de java.* y de otros elementos del dominio")
    void elDominioNoDependeDeFrameworks() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideOutsideOfPackages("java..", "..domain..")
                .because("el dominio expresa reglas de negocio y no debe conocer Spring, JPA, "
                        + "Jackson, el SDK de AWS ni Lombok");

        regla.check(classes);
    }

    @Test
    @DisplayName("La capa de aplicación no depende de infraestructura ni de frameworks de entrada")
    void laAplicacionNoDependeDeInfrastructure() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure..",
                        "..shared..",
                        "org.springframework.web..",
                        "org.springframework.data..",
                        "com.fasterxml.jackson..",
                        "tools.jackson..",
                        "software.amazon.awssdk..")
                .because("los casos de uso orquestan puertos; no conocen HTTP, JPA ni el SDK de AWS");

        regla.check(classes);
    }

    @Test
    @DisplayName("Los adaptadores de entrada no dependen de los de salida ni de entidades JPA")
    void losAdaptadoresDeEntradaNoConocenLaSalida() {
        ArchRule regla = noClasses()
                .that().resideInAPackage(P + "infrastructure.adapter.in..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        P + "infrastructure.adapter.out..",
                        "jakarta.persistence..")
                .because("el adaptador web habla con la aplicación, nunca con la persistencia");

        regla.check(classes);
    }

    @Test
    @DisplayName("Los adaptadores de persistencia no dependen de los de entrada")
    void losAdaptadoresDeSalidaNoConocenLaEntrada() {
        ArchRule regla = noClasses()
                .that().resideInAPackage(P + "infrastructure.adapter.out.persistence..")
                .should().dependOnClassesThat().resideInAPackage(P + "infrastructure.adapter.in..")
                .because("el adaptador de salida implementa el puerto; no invoca al de entrada");

        regla.check(classes);
    }

    @Test
    @DisplayName("Los adaptadores de almacenamiento y mensajería no tocan el modelo ni JPA")
    void losAdaptadoresDeInfraestructuraEstanAislados() {
        // El puerto sí está en el dominio (domain.port.out), pero el modelo de negocio no. Un
        // adaptador de S3 habla de claves y de bytes, nunca de pedidos ni de pizzas.
        ArchRule regla = noClasses()
                .that().resideInAnyPackage(
                        P + "infrastructure.adapter.out.storage..",
                        P + "infrastructure.adapter.out.messaging..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        P + "infrastructure.adapter.in..",
                        P + "domain.model..",
                        P + "domain.exception..",
                        "jakarta.persistence..")
                .because("S3 y SQS se comunican con la aplicación a través de puertos, "
                        + "nunca con el dominio de negocio");

        regla.check(classes);
    }

    @Test
    @DisplayName("shared es una capa transversal: no conoce casos de uso ni adaptadores de salida")
    void sharedNoConoceElNucleo() {
        ArchRule regla = noClasses()
                .that().resideInAPackage(P + "shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        P + "application..",
                        P + "domain.model..",
                        P + "domain.port..",
                        P + "infrastructure.adapter.out..")
                .because("shared traduce y registra; si conoce un caso de uso deja de ser transversal "
                        + "y se convierte en la capa que lo sabe todo");

        regla.check(classes);
    }

    @Test
    @DisplayName("Las reglas tienen materia: el importador encuentra el dominio")
    void lasReglasNoEstanVaciasPorAcierto() {
        // archunit.properties permite la regla vacía porque todavía no existen los paquetes
        // application ni los adaptadores de salida. Esta prueba evita que ese ajuste se convierta en
        // una cobertura illusoria: si el importador dejara de encontrar clases, o si el dominio
        // desapareciera sin que nadie se entere, falla aquí.
        Set<String> paquetesDelDominio = classes.stream()
                .map(JavaClass::getPackageName)
                .filter(nombrePaquete -> nombrePaquete.startsWith(BASE + ".domain"))
                .collect(Collectors.toSet());

        assertFalse(paquetesDelDominio.isEmpty(),
                "No se ha importado ninguna clase de dominio: las reglas no estarían protegiendo nada");
    }

    @Test
    @DisplayName("Bajo com.pizzeria sólo existen los cuatro paquetes de primer nivel permitidos")
    void noHayPaquetesHuerfanos() {
        Set<String> primerNivel = classes.stream()
                .map(ArchitectureRulesTest::primerNivelDe)
                .filter(segmento -> !segmento.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        // Un paquete nuevo de primer nivel significa que alguien ha decidido algo sobre la
        // arquitectura sin escribirlo en ninguna parte. Esto es exactamente lo que se quiere ver.
        assertTrue(PAQUETES_DE_PRIMER_NIVEL.containsAll(primerNivel),
                "Paquetes de primer nivel no previstos bajo " + BASE + ": "
                        + primerNivel.stream().filter(s -> !PAQUETES_DE_PRIMER_NIVEL.contains(s)).toList()
                        + ". Los permitidos son " + new TreeSet<>(PAQUETES_DE_PRIMER_NIVEL));
    }

    /**
     * Primer segmento del paquete después de {@code com.pizzeria}, o cadena vacía si la clase vive
     * directamente en la raíz (el caso de {@code PizzeriaApplication}).
     */
    private static String primerNivelDe(JavaClass clase) {
        String nombrePaquete = clase.getPackageName();
        if (nombrePaquete.length() <= BASE.length()) {
            // La clase vive directamente en com.pizzeria: no cuelga de ningún nivel.
            return "";
        }
        String relativo = nombrePaquete.substring(BASE.length() + 1);
        int corte = relativo.indexOf('.');
        return corte < 0 ? relativo : relativo.substring(0, corte);
    }
}