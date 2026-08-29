package com.rabbit.app.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * {@code @TrackedOperation} 不能贴在纯转调的重载上。
 *
 * <p>这条规则来自一次真实事故：注解贴在 {@code RabbitService} 四参的
 * {@code createRabbit} 上，而控制器走的是五参方法，于是单只建兔整整一个版本
 * 没有留痕。代码看着完全正常——注解在、eventType 也在——只是没人调那个方法。
 *
 * <p>端到端覆盖（{@code OperationEventCoverageIT}）能抓到被测到的入口，
 * 这条规则负责剩下的：任何新加的注解只要落在自转调重载上就直接构建失败，
 * 不用等某天有人发现流水里缺了一类操作。
 *
 * <p>之所以扫源码而不是用反射：转调关系在字节码里同样看得见，但源码扫描
 * 不需要加载整个上下文，也能把文件名和行号直接指给写代码的人。
 */
class TrackedOperationPlacementTest {

    private static final Pattern ANNOTATION = Pattern.compile("@TrackedOperation\\b");

    @Test
    void aTrackedOperationNeverSitsOnASelfDelegatingOverload() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : javaSources()) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (!ANNOTATION.matcher(lines.get(i)).find()) {
                    continue;
                }
                int signatureLine = skipAnnotations(lines, i);
                String methodName = methodName(lines, signatureLine);
                if (methodName == null) {
                    continue;
                }
                if (delegatesToItsOwnOverload(lines, signatureLine, methodName)) {
                    offenders.add(
                        file.getFileName() + ":" + (i + 1) + " " + methodName
                            + " —— 注解贴在只转调同名重载的方法上，真实调用方可能绕过它"
                    );
                }
            }
        }

        assertTrue(
            offenders.isEmpty(),
            "以下 @TrackedOperation 落在自转调重载上，留痕会静默丢失：\n" + String.join("\n", offenders)
        );
    }

    private List<Path> javaSources() throws IOException {
        List<Path> roots = List.of(
            Path.of("..", "rabbit-production", "src", "main", "java"),
            Path.of("..", "rabbit-access", "src", "main", "java"),
            Path.of("..", "rabbit-reporting", "src", "main", "java"),
            Path.of("..", "rabbit-platform", "src", "main", "java")
        );
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            }
        }
        return files;
    }

    /** 跳过注解块（可能跨行），返回方法签名所在行。 */
    private int skipAnnotations(List<String> lines, int annotationLine) {
        int depth = 0;
        int index = annotationLine;
        do {
            String line = lines.get(index);
            depth += count(line, '(') - count(line, ')');
            index++;
        } while (depth > 0 && index < lines.size());

        while (index < lines.size()) {
            String trimmed = lines.get(index).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("@") || trimmed.startsWith("//")) {
                index++;
                continue;
            }
            return index;
        }
        return lines.size() - 1;
    }

    private String methodName(List<String> lines, int signatureLine) {
        Matcher matcher = Pattern.compile("\\b(\\w+)\\s*\\(").matcher(lines.get(signatureLine));
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 方法体是否只是把活儿转给同名重载。
     *
     * <p>同类自调用绕过 Spring 代理，所以这种转调等于让注解对内部调用方失效；
     * 而外部调用方一旦改调另一个重载，注解就彻底空转。
     */
    private boolean delegatesToItsOwnOverload(List<String> lines, int signatureLine, String methodName) {
        int bodyStart = signatureLine;
        while (bodyStart < lines.size() && !lines.get(bodyStart).contains("{")) {
            bodyStart++;
        }
        Pattern selfCall = Pattern.compile("^(return\\s+)?" + Pattern.quote(methodName) + "\\s*\\(");
        for (int i = bodyStart + 1; i < Math.min(bodyStart + 6, lines.size()); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.equals("}")) {
                return false;
            }
            return selfCall.matcher(trimmed).find();
        }
        return false;
    }

    private int count(String value, char target) {
        int total = 0;
        for (char c : value.toCharArray()) {
            if (c == target) {
                total++;
            }
        }
        return total;
    }
}
