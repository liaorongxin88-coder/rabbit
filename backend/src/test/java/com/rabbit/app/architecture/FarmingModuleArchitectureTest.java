package com.rabbit.app.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.modules.batch.service.BatchAphrodisiacService;
import com.rabbit.app.modules.batch.service.BatchBreedingService;
import com.rabbit.app.modules.batch.service.BatchLifecycleService;
import com.rabbit.app.modules.batch.service.BatchParturitionService;
import com.rabbit.app.modules.batch.service.BatchSaleService;
import com.rabbit.app.modules.batch.service.BatchService;
import com.rabbit.app.modules.batch.service.BatchWeaningService;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class FarmingModuleArchitectureTest {
    private static final String MODULES_PACKAGE = "com.rabbit.app.modules";
    private static final String WORKSPACE_PACKAGE = MODULES_PACKAGE + ".workspace";
    private static final String WORKSPACE_MODEL_PACKAGE = WORKSPACE_PACKAGE + ".model";
    private static final String WORKSPACE_SPI_PACKAGE = WORKSPACE_PACKAGE + ".spi";
    private static final String CACHE_PACKAGE = "com.rabbit.app.cache";
    private static final String BATCH_FACADE = MODULES_PACKAGE + ".batch.service.BatchService";

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
    void controllersDoNotAccessMappersDirectly() {
        Set<String> violations = dependencies()
                .filter(dependency -> dependency.getOriginClass().getPackageName().contains(".controller"))
                .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".mapper"))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "controllers must delegate persistence access to services:\n" + String.join("\n", violations)
        );
    }

    @Test
    void genericCacheDoesNotDependOnBusinessModules() {
        Set<String> violations = dependencies()
                .filter(dependency -> isInPackage(dependency.getOriginClass().getPackageName(), CACHE_PACKAGE))
                .filter(dependency -> isInPackage(dependency.getTargetClass().getPackageName(), MODULES_PACKAGE))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "generic cache must not depend on business modules:\n" + String.join("\n", violations)
        );
    }

    @Test
    void batchFacadeDoesNotAccessPersistenceDirectly() {
        Set<String> violations = dependencies()
                .filter(dependency -> dependency.getOriginClass().getName().equals(BATCH_FACADE))
                .filter(dependency -> dependency.getTargetClass().getPackageName().contains(".mapper"))
                .map(Dependency::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "batch facade must delegate persistence to focused workflow services:\n"
                        + String.join("\n", violations)
        );
    }

    @Test
    void batchTransactionsLiveInFocusedWorkflowServices() {
        Set<Class<?>> workflowServices = Set.of(
                BatchLifecycleService.class,
                BatchBreedingService.class,
                BatchParturitionService.class,
                BatchWeaningService.class,
                BatchAphrodisiacService.class,
                BatchSaleService.class
        );
        Set<String> missingTransactions = workflowServices.stream()
                .flatMap(type -> Stream.of(type.getDeclaredMethods()))
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !method.isAnnotationPresent(Transactional.class))
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> facadeTransactions = Stream.of(BatchService.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Transactional.class))
                .map(method -> method.getName())
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                missingTransactions.isEmpty(),
                () -> "batch workflow entry points must own transactions:\n"
                        + String.join("\n", missingTransactions)
        );
        assertTrue(
                facadeTransactions.isEmpty(),
                () -> "batch facade must not own transactions:\n" + String.join("\n", facadeTransactions)
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
