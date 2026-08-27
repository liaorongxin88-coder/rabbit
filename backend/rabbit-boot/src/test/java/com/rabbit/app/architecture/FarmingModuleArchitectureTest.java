package com.rabbit.app.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.tracking.TrackedOperation;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.lang.annotation.Annotation;
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
    private static final Set<String> PLATFORM_PACKAGES = Set.of(
            "com.rabbit.app.cache",
            "com.rabbit.app.common",
            "com.rabbit.app.util",
            MODULES_PACKAGE + ".dedup"
    );
    private static final Set<String> ACCESS_PACKAGES = Set.of(
            "com.rabbit.app.security",
            "com.rabbit.app.tracking",
            MODULES_PACKAGE + ".auth",
            MODULES_PACKAGE + ".house",
            MODULES_PACKAGE + ".workspace"
    );

    /**
     * 已知且已判定无害的 {@code @Transactional} 同类自调用。
     *
     * <p>全仓 86 处 {@code @Transactional} 逐一排查后，真正存在同类自调用的只有
     * 这几处，且被调方的传播行为都是默认的 {@code REQUIRED}——调用方要么自己
     * 已在事务里（自调用绕过代理不改变最终语义），要么只被事务方法调用。
     * 它们不需要改，但<b>新增</b>的自调用必须被拦下来：一旦被调方将来改成
     * {@code REQUIRES_NEW} 或加上 {@code @TrackedOperation}，绕过代理就从无害
     * 变成静默失效，而这种失效没有任何运行期信号。
     *
     * <p>清单的完整版本见 {@code docs/project/transactional-self-invocation-audit.md}。
     */
    private static final Set<String> ALLOWED_TRANSACTIONAL_SELF_CALLS = Set.of(
            "SettingService#updateUserSetting -> getOrCreateUserSetting",
            "SettingService#getEffectiveSetting -> getOrCreateUserSetting",
            "SettingService#updateHouseSetting -> getOrCreateUserSetting",
            "SettingService#getOrCreateHouseSetting -> getOrCreateUserSetting",
            "PhoneAuthService#loginOrRegister -> authenticate",
            "RequestDedupService#begin -> begin",
            "RabbitService#createRabbit -> createRabbit"
    );
    private static final Set<String> PRODUCTION_PACKAGES = Set.of(
            MODULES_PACKAGE + ".appupdate",
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
            MODULES_PACKAGE + ".vaccination",
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

    /**
     * Spring AOP 不拦截同类自调用。对 {@code @TrackedOperation} 来说，这意味着
     * 上下文不绑、幂等不记账、事件不落库——而方法照常执行、照常返回，
     * 没有异常也没有日志。事件流会出现无声空洞，测试测不出来。
     *
     * <p>所以这条规则零容忍：要在类内部复用被追踪的逻辑，把逻辑抽成不带注解的
     * 私有方法，让两个入口各自被代理。
     */
    @Test
    void trackedOperationsAreNeverInvokedFromTheSameClass() {
        Set<String> violations = selfInvocations(TrackedOperation.class)
                .map(JavaMethodCall::getDescription)
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "@TrackedOperation 方法不能被同类自调用（Spring AOP 走代理，自调用会让注解静默失效）:\n"
                        + String.join("\n", violations)
        );
    }

    @Test
    void noNewTransactionalSelfInvocationsAppear() {
        Set<String> violations = selfInvocations(Transactional.class)
                .filter(call -> !ALLOWED_TRANSACTIONAL_SELF_CALLS.contains(selfCallKey(call)))
                .map(call -> selfCallKey(call) + "  " + call.getDescription())
                .collect(Collectors.toCollection(TreeSet::new));

        assertTrue(
                violations.isEmpty(),
                () -> "新增的 @Transactional 同类自调用会绕过代理。确认无害后加入 "
                        + "ALLOWED_TRANSACTIONAL_SELF_CALLS 并更新 "
                        + "docs/project/transactional-self-invocation-audit.md:\n"
                        + String.join("\n", violations)
        );
    }

    private Stream<JavaMethodCall> selfInvocations(Class<? extends Annotation> annotation) {
        return productionClasses.stream()
                .flatMap(javaClass -> javaClass.getMethodCallsFromSelf().stream())
                .filter(call -> call.getOriginOwner().equals(call.getTargetOwner()))
                .filter(call -> !call.getOrigin().getName().equals(call.getTarget().getName()) || isOverload(call))
                .filter(call -> call.getTarget()
                        .resolveMember()
                        .map(member -> member.isAnnotatedWith(annotation))
                        .orElse(false));
    }

    /**
     * 同名调用通常是递归，不是自调用问题；但重载之间的调用是真的
     * （{@code begin(4)} 调 {@code begin(5)} 就是一例），必须留下。
     */
    private boolean isOverload(JavaMethodCall call) {
        return !call.getOrigin().getFullName().equals(call.getTarget().getFullName());
    }

    private String selfCallKey(JavaMethodCall call) {
        return call.getOriginOwner().getSimpleName()
                + "#" + call.getOrigin().getName()
                + " -> " + call.getTarget().getName();
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
