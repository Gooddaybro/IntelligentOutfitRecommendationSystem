package com.recommendation.intelligentoutfitrecommendationsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class)
            .withPropertyValues("app.jwt.secret=test-jwt-secret-with-at-least-32-bytes");

    @Test
    void nonWebApplicationDoesNotCreateServletSecurityFilterChain() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
        });
    }
}
