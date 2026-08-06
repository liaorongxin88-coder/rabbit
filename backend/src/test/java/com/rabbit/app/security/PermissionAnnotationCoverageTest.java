package com.rabbit.app.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rabbit.app.security.permission.RequiresPermission;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class PermissionAnnotationCoverageTest {
    private static final Set<String> PUBLIC_ENDPOINTS = Set.of(
            "AdminAuthController#login",
            "AuthController#register",
            "AuthController#login",
            "AuthController#sendSmsCode",
            "AuthController#phoneLogin",
            "AuthController#wechatLogin"
    );

    @Test
    void everyControllerEndpointHasAnExplicitPermissionUnlessPublic() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        Set<String> missing = new HashSet<String>();
        for (var component : scanner.findCandidateComponents("com.rabbit.app.modules")) {
            Class<?> controller = Class.forName(component.getBeanClassName());
            RequiresPermission classPermission =
                    AnnotatedElementUtils.findMergedAnnotation(controller, RequiresPermission.class);
            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                String endpoint = controller.getSimpleName() + "#" + method.getName();
                RequiresPermission methodPermission =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequiresPermission.class);
                if (!PUBLIC_ENDPOINTS.contains(endpoint) && classPermission == null && methodPermission == null) {
                    missing.add(endpoint);
                }
            }
        }

        assertTrue(missing.isEmpty(), "Endpoints without permission declarations: " + missing);
    }
}
