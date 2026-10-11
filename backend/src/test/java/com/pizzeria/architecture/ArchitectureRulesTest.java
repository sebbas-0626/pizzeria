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
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifica las reglas de dependencia entre capas definidas en
 * {@code docs/development/spring-conventions.md} §1.
 *
 * <p>Una regla de arquitectura que sólo está escrita en un documento se rompe sin avisar.
 * Estas pruebas la convierten en un fallo de build.
 *
 * <p>Se usa el módulo {@code core} de ArchUnit y no {@code archunit-junit5}: las reglas se
 * ejecutan desde tests JUnit normales, sin atar el proyecto al motor de ArchUnit ni a su
 * compatibilidad con la versión de JUnit que fije Spring Boot.
 */
class ArchitectureRulesTest {

    private static final String BASE = "com.pizzeria";

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
    @DisplayName("Las reglas tienen materia: el importador encuentra el dominio")
    void lasReglasNoEstanVaciasPorAcierto() {
        // archunit.properties permite la regla vacía porque en US-001 todavía no existen
        // los paquetes application ni adapters. Esta prueba evita que ese ajuste se
        // convierta en una cobertura illusionsoria: si el importador dejara de encontrar
        // clases, o si el dominio desapareciera sin que nadie se entere, falla aquí.
        Set<String> paquetesDelDominio = classes.stream()
                .map(JavaClass::getPackageName)
                .filter(nombrePaquete -> nombrePaquete.endsWith(".domain"))
                .collect(Collectors.toSet());

        assertFalse(paquetesDelDominio.isEmpty(),
                "No se ha importado ninguna clase de dominio: las reglas no estarían protegiendo nada");
    }

    @Test
    @DisplayName("La capa de aplicación no depende de adaptadores, web, persistencia ni AWS")
    void laAplicacionNoDependeDeAdaptadores() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapters..",
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
                .that().resideInAPackage("..adapters.in.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapters.out..",
                        "jakarta.persistence..")
                .because("el adaptador web habla con la aplicación, nunca con la persistencia");

        regla.check(classes);
    }

    @Test
    @DisplayName("Los adaptadores de persistencia no dependen de los de entrada")
    void losAdaptadoresDeSalidaNoConocenLaEntrada() {
        ArchRule regla = noClasses()
                .that().resideInAPackage("..adapters.out.persistence..")
                .should().dependOnClassesThat().resideInAPackage("..adapters.in..")
                .because("el adaptador de salida implementa el puerto; no invoca al de entrada");

        regla.check(classes);
    }

    @Test
    @DisplayName("Los adaptadores de almacenamiento y mensajería no tocan dominio ni JPA")
    void losAdaptadoresDeInfraestructuraEstanAislados() {
        ArchRule regla = noClasses()
                .that().resideInAnyPackage("..adapters.out.storage..", "..adapters.out.messaging..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..adapters.in..",
                        "..domain..",
                        "jakarta.persistence..")
                .because("S3 y SQS se comunican con la aplicación a través de puertos, "
                        + "nunca con el dominio");

        regla.check(classes);
    }
}
