package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void externalServiceErrorsUseSafePublicMessage() {
        var response = handler.handleExternalService(new ExternalServiceException("provider token leaked in detail"));

        assertThat(response.errorCode()).isEqualTo("external_service_error");
        assertThat(response.message()).isEqualTo("External service is temporarily unavailable.");
        assertThat(response.message()).doesNotContain("token");
    }

    @Test
    void unexpectedErrorsUseSafePublicMessage() {
        var response = handler.handleUnexpected(new RuntimeException("stack trace with secret prompt"));

        assertThat(response.errorCode()).isEqualTo("internal_server_error");
        assertThat(response.message()).isEqualTo("Request failed. Please try again later.");
        assertThat(response.message()).doesNotContain("secret");
    }

    @Test
    void idempotencyConflictsUseStableBusinessCode() {
        var response = handler.handleIdempotencyConflict(
                new IdempotencyKeyConflictException(
                        "Idempotency-Key was already used with different request parameters"
                )
        );

        assertThat(response.errorCode()).isEqualTo("idempotency_key_reused");
        assertThat(response.message())
                .isEqualTo("Idempotency-Key was already used with different request parameters");
    }
}
