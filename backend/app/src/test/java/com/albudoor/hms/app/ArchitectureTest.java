package com.albudoor.hms.app;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Module boundary enforcement.
 *
 * Rules:
 *   - Modules may depend on platform, but not on each other except through public API surfaces
 *     defined in their root package (e.g., domain events, query DTOs).
 *   - Domain layer must not depend on Spring or JPA infrastructure leaking outside its package.
 *   - Within a module, vertical slices must not call each other's internals — they share only
 *     the module's domain layer.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.albudoor.hms");

    @Test
    void platformDoesNotDependOnAnyOtherModule() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..identity..", "..patientregistry..");
        rule.check(CLASSES);
    }

    @Test
    void identityDoesNotDependOnPatientRegistry() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity..")
                .should().dependOnClassesThat().resideInAPackage("..patientregistry..");
        rule.check(CLASSES);
    }

    @Test
    void domainPackagesAreFreeOfWebAndPersistenceLeaks() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.stereotype..",
                        "org.springframework.security..");
        rule.check(CLASSES);
    }

    @Test
    void controllersLiveOnlyInsideSlicePackages() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideOutsideOfPackages("..domain..", "..infrastructure..");
        rule.check(CLASSES);
    }

    @Test
    void layeredWithinPatientRegistry() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..patientregistry.domain..")
                .layer("application").definedBy(
                        "..patientregistry.registernewpatient..",
                        "..patientregistry.registerinfant..",
                        "..patientregistry.searchpatient..",
                        "..patientregistry.togglevip..")
                .layer("infrastructure").definedBy("..patientregistry.infrastructure..")
                .whereLayer("application").mayOnlyAccessLayers("domain", "infrastructure")
                .whereLayer("infrastructure").mayOnlyAccessLayers("domain");
        rule.check(CLASSES);
    }

    @Test
    void layeredWithinPremature() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..premature.domain..")
                .layer("application").definedBy(
                        "..premature.createbed..",
                        "..premature.updatebed..",
                        "..premature.listbeds..",
                        "..premature.admitpatient..",
                        "..premature.extendstay..",
                        "..premature.finishtreatment..",
                        "..premature.reissuedischargepayment..",
                        "..premature.listadmissions..",
                        "..premature.getcase..",
                        "..premature.upsertform..",
                        "..premature.recordtour..",
                        "..premature.signature..",
                        "..premature.bridge..")
                .layer("infrastructure").definedBy("..premature.infrastructure..")
                .whereLayer("application").mayOnlyAccessLayers("domain", "infrastructure")
                .whereLayer("infrastructure").mayOnlyAccessLayers("domain");
        rule.check(CLASSES);
    }

    @Test
    void layeredWithinEmergency() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..emergency.domain..")
                .layer("application").definedBy(
                        "..emergency.createbed..",
                        "..emergency.updatebed..",
                        "..emergency.listbeds..",
                        "..emergency.listservices..",
                        "..emergency.admitpatient..",
                        "..emergency.extendstay..",
                        "..emergency.finishtreatment..",
                        "..emergency.reissuedischargepayment..",
                        "..emergency.listcases..",
                        "..emergency.bridge..")
                .layer("infrastructure").definedBy("..emergency.infrastructure..")
                .whereLayer("application").mayOnlyAccessLayers("domain", "infrastructure")
                .whereLayer("infrastructure").mayOnlyAccessLayers("domain");
        rule.check(CLASSES);
    }

    @Test
    void bedStayFormsDoesNotDependOnDepartmentModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..bedstayforms..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..premature..", "..emergency..");
        rule.check(CLASSES);
    }
}
