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

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.rabbit.app.modules");

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

    private boolean isInPackage(String packageName, String prefix) {
        return packageName.equals(prefix) || packageName.startsWith(prefix + ".");
    }
}
