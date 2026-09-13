package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProRunRegistryFailureTests {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ProRunRegistry registry = new ProRunRegistry(redis);

    @Test
    void missingCredentialNeverReachesRedis() {
        var error = assertThrows(ProRunRegistry.RegistryException.class,
                () -> registry.authorize("01234567-1234-1234-1234-123456789012", null));
        assertThat(error.failure()).isEqualTo(ProRunRegistry.Failure.FORBIDDEN);
        verifyNoInteractions(redis);
    }

    @Test
    void nullRedisScriptResultDoesNotCreateAnAuthorizedRun() {
        var error = assertThrows(ProRunRegistry.RegistryException.class,
                () -> registry.create(7L, "thread", "request"));
        assertThat(error.failure()).isEqualTo(ProRunRegistry.Failure.UNAVAILABLE);
        assertThat(error.getMessage()).isEqualTo("Pro run unavailable");
    }
}
