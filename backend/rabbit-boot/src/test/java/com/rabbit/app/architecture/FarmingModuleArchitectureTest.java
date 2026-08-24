package com.rabbit.app.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class FarmingModuleArchitectureTest {
    private static final String MODULES_PACKAGE = "com.rabbit.app.modules";
    private static final String WORKSPACE_PACKAGE = MODULES_PACKAGE + ".workspace";
    private static final String WORKSPACE_MODEL_PACKAGE = WORKSPACE_PACKAGE + ".model";
    private static final String WORKSPACE_SPI_PACKAGE = WORKSPACE_PACKAGE + ".spi";
    private static final Set<String> PLATFORM_PACKAGES = Set.of(
            "com.rabbit.app.cache",
            "com.rabbit.app.common",
            "com.rabbit.app.util",
            MODULES_PACKAGE + ".dedup"
    );
    private static final Set<String> ACCESS_PACKAGES = Set.of(
            "com.rabbit.app.security",
            MODULES_PACKAGE + ".auth",
            MODULES_PACKAGE + ".house",
            MODULES_PACKAGE + ".workspace",
            MODULES_PACKAGE + ".apprelease"
    );
    private static final Set<String> PRODUCTION_PACKAGES = Set.of(
            MODULES_PACKAGE + ".batch",
            MODULES_PACKAGE + ".cage",
            MODULES_PACKAGE + ".event",
            MODULES_PACKAGE + ".feed",
            MODULES_PACKAGE + ".file",
            MODULES_PACKAGE + ".hardware",
            MODULES_PACKAGE + ".inventory",
            MODULES_PACKAGE + ".nfc",
            MODULES_PACKAGE + ".outbound",
            MODULES_PACKAGE + ".rabbit",
            MODULES_PACKAGE + ".repro",
            MODULES_PACKAGE + ".sale",
            MODULES_PACKAGE + ".setting",
            MODULES_PACKAGE + ".treatment",
            MODULES_PACKAGE + ".weight"
    );
    private static final Set<String> REPORTING_PACKAGES = Set.of(
            MODULES_PACKAGE + ".admin",
            MODULES_PACKAGE + ".audit",
            MODULES_PACKAGE + ".report"
    );
    private static final Set<String> BOOT_PACKAGES = Set.of("com.rabbit.app.config");

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.rabbit.app");

    @Test
    void workspaceCoreDoesNotDependOnConcreteBusinessModules() {
        Set<String> violations = dependencies()
                .filter(dependency -> isInPackage(dependency.getOriginClass().getPackageName(), WORKSPACE_PACKAGE))
                .filter(dependency -> isConcreteBusinessModule(dependency.getTargetClass().getPackageName()))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "workspace core must not depend on concrete business modules:\n" + String.join("\n", violations)
        );
    }

    @Test
    void concreteModulesOnlyUseTheWorkspaceModelAndSpi() {
        Set<String> violations = dependencies()
                .filter(dependency -> !isInPackage(dependency.getOriginClass().getPackageName(), WORKSPACE_PACKAGE))
                .filter(dependency -> isInPackage(dependency.getTargetClass().getPackageName(), WORKSPACE_PACKAGE))
                .filter(dependency -> !isPublicWorkspaceContract(dependency.getTargetClass().getPackageName()))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "concrete modules may only use workspace model and spi packages:\n"
                        + String.join("\n", violations)
        );
    }

    @Test
    void platformDoesNotDependOnBusinessOrBootModules() {
        assertNoDependencies(
                "platform must not depend on business or boot modules",
                PLATFORM_PACKAGES,
                Stream.of(ACCESS_PACKAGES, PRODUCTION_PACKAGES, REPORTING_PACKAGES, BOOT_PACKAGES)
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())
        );
    }

    @Test
    void accessDoesNotDependOnProductionReportingOrBootModules() {
        assertNoDependencies(
                "access must not depend on production, reporting, or boot modules",
                ACCESS_PACKAGES,
                Stream.of(PRODUCTION_PACKAGES, REPORTING_PACKAGES, BOOT_PACKAGES)
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet())
        );
    }

    @Test
    void productionDoesNotDependOnReportingOrBootModules() {
        assertNoDependencies(
                "production must not depend on reporting or boot modules",
                PRODUCTION_PACKAGES,
                Stream.concat(REPORTING_PACKAGES.stream(), BOOT_PACKAGES.stream()).collect(Collectors.toSet())
        );
    }

    @Test
    void reportingDoesNotDependOnBootModule() {
        assertNoDependencies("reporting must not depend on the boot module", REPORTING_PACKAGES, BOOT_PACKAGES);
    }

    private void assertNoDependencies(String message, Set<String> origins, Set<String> targets) {
        Set<String> violations = dependencies()
                .filter(dependency -> isInAnyPackage(dependency.getOriginClass().getPackageName(), origins))
                .filter(dependency -> isInAnyPackage(dependency.getTargetClass().getPackageName(), targets))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(violations.isEmpty(), () -> message + ":\n" + String.join("\n", violations));
    }

    private Stream<Dependency> dependencies() {
        return productionClasses.stream().flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream());
    }

    private boolean isConcreteBusinessModule(String packageName) {
        return isInPackage(packageName, MODULES_PACKAGE) && !isInPackage(packageName, WORKSPACE_PACKAGE);
    }

    private boolean isPublicWorkspaceContract(String packageName) {
        return isInPackage(packageName, WORKSPACE_MODEL_PACKAGE)
                || isInPackage(packageName, WORKSPACE_SPI_PACKAGE);
    }

    private boolean isInAnyPackage(String packageName, Set<String> prefixes) {
        return prefixes.stream().anyMatch(prefix -> isInPackage(packageName, prefix));
    }

    private boolean isInPackage(String packageName, String prefix) {
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }
}
