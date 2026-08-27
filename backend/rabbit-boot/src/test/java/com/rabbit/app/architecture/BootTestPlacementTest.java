package com.rabbit.app.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 守住「单测跟着业务代码走」这条线。
 *
 * <p>背景：这四个模块的测试原本全堆在 rabbit-boot —— boot 只有 5 个主类，却扛着 173 个
 * 单测，而 rabbit-production 有 284 个主类、0 个测试。下沉之后如果没人看着，新测试会因为
 * 「boot 里依赖最全、随手就能写」而慢慢流回来，几个月后又是原样。
 *
 * <p>所以 boot 里只允许留下**确实只能在这里跑**的测试，且每个都要在下面写明理由。
 * 集成测试（{@code *IT.java}）不在此列：它们要完整的应用上下文，本来就属于 boot。
 */
class BootTestPlacementTest {
    /**
     * 允许留在 boot 的单测，以及为什么只能在这里。
     *
     * <p>往这里加名字之前先问一句：它是真的需要「看见所有模块」，还是只是图省事？
     * 如果被测类属于某个具体模块，那它的测试就该跟着那个模块走。
     */
    private static final Set<String> ALLOWED = Set.of(
            // ArchUnit 要 importPackages("com.rabbit.app")，只有 boot 能看见全部模块。
            "FarmingModuleArchitectureTest",
            // 自己，同样要扫 boot 的测试目录。
            "BootTestPlacementTest",
            // 扫 com.rabbit.app.modules 下所有 @RestController，需要全模块类路径。
            "PermissionAnnotationCoverageTest",
            // 读 boot 资源目录下的 db/schema.sql。
            "SchemaSqlV24Test",
            // 被测类 SecurityConfig 就在 boot。
            "SecurityConfigTest"
    );

    @Test
    void bootOnlyKeepsTestsThatCannotLiveInABusinessModule() {
        Path testRoot = Paths.get("src", "test", "java");
        assertTrue(
                Files.isDirectory(testRoot),
                () -> "找不到 " + testRoot.toAbsolutePath() + "，本用例假定工作目录是 rabbit-boot 模块根"
        );

        Set<String> unexpected;
        try (Stream<Path> files = Files.walk(testRoot)) {
            unexpected = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith("Test.java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .filter(name -> !ALLOWED.contains(name))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException e) {
            throw new UncheckedIOException("无法遍历 " + testRoot.toAbsolutePath(), e);
        }

        assertTrue(
                unexpected.isEmpty(),
                () -> "这些单测应该下沉到被测类所在的模块，而不是留在 rabbit-boot：\n"
                        + String.join("\n", unexpected)
                        + "\n\n确实只能在 boot 跑的话，把它加进 ALLOWED 并写明理由。"
        );
    }
}
