package dev.blackice.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class BackendArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("dev.blackice");

    @Test
    void production_code_does_not_reside_in_legacy_features_package() {
        noClasses()
            .should().resideInAPackage("dev.blackice.features..")
            .check(classes);
    }

    @Test
    void application_does_not_depend_on_infrastructure() {
        noClasses().that().resideInAPackage("dev.blackice..application..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice..infrastructure..")
            .check(classes);
    }

    @Test
    void application_does_not_depend_on_http_boundary() {
        noClasses().that().resideInAPackage("dev.blackice..application..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice..api..")
            .check(classes);
    }

    @Test
    void application_does_not_depend_on_jakarta_rest() {
        noClasses().that().resideInAPackage("dev.blackice..application..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.ws.rs..")
            .check(classes);
    }

    @Test
    void application_does_not_depend_on_opentelemetry() {
        noClasses().that().resideInAPackage("dev.blackice..application..")
            .should().dependOnClassesThat().resideInAPackage("io.opentelemetry..")
            .check(classes);
    }

    @Test
    void only_the_api_boundary_reads_the_trace_context() {
        noClasses().that().resideOutsideOfPackages("dev.blackice..api..", "dev.blackice..infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("io.opentelemetry..")
            .check(classes);
    }

    @Test
    void ingest_application_has_no_production_classes_in_root_package() {
        noClasses()
            .should().resideInAPackage("dev.blackice.ingest.application")
            .check(classes);
    }

    @Test
    void ingest_module_does_not_consume_security_infrastructure() {
        noClasses().that().resideInAPackage("dev.blackice.ingest..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice.security.infrastructure..")
            .check(classes);
    }

    @Test
    void security_module_does_not_consume_ingest_infrastructure() {
        noClasses().that().resideInAPackage("dev.blackice.security..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice.ingest.infrastructure..")
            .check(classes);
    }

    @Test
    void worklist_application_has_no_production_classes_in_root_package() {
        noClasses()
            .should().resideInAPackage("dev.blackice.worklist.application")
            .check(classes);
    }

    @Test
    void worklist_module_does_not_consume_security_infrastructure() {
        noClasses().that().resideInAPackage("dev.blackice.worklist..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice.security.infrastructure..")
            .check(classes);
    }

    @Test
    void security_module_does_not_consume_worklist_infrastructure() {
        noClasses().that().resideInAPackage("dev.blackice.security..")
            .should().dependOnClassesThat().resideInAPackage("dev.blackice.worklist.infrastructure..")
            .check(classes);
    }
}
