package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.api.InternalAssistantToolController;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProToolResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProToolQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.GlobalExceptionHandler;
import com.recommendation.intelligentoutfitrecommendationsystem.common.internal.InternalApiInterceptor;
import com.recommendation.intelligentoutfitrecommendationsystem.common.internal.InternalApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalAssistantToolControllerTests {
    private static final String URL = "/internal/assistant/runs/run-1/tools/search_products";
    private final ProToolQueryService service = mock(ProToolQueryService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalAssistantToolController(service))
                .addInterceptors(new InternalApiInterceptor(new InternalApiProperties("service-secret")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingServiceCredentialBeforeCallingQuery() throws Exception {
        mvc.perform(post(URL).header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsWrongServiceCredentialBeforeCallingQuery() throws Exception {
        mvc.perform(post(URL).header("X-Internal-Token", "wrong")
                        .header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void passesRawArgumentsAndBothBoundIdentifiersToService() throws Exception {
        when(service.execute(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(new ProToolResult("ok", List.of(Map.of("spu_id", 1, "sku_id", 2,
                        "sale_price", "299.90")), "ev-1", "java", List.of(), null));
        mvc.perform(post(URL).header("X-Internal-Token", "service-secret")
                        .header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"budget_max\":\"299.90\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.evidence_id").value("ev-1"))
                .andExpect(jsonPath("$.missing_fields").isArray())
                .andExpect(jsonPath("$.data[0].sale_price").value("299.90"))
                .andExpect(jsonPath("$.tool_run_token").doesNotExist());
        verify(service).execute("run-1", "run-secret", "search_products", Map.of("budget_max", "299.90"));
    }

    @Test
    void invalidArgumentsHaveStructuredClientError() throws Exception {
        when(service.execute(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new BadRequestException("unknown argument"));
        mvc.perform(post(URL).header("X-Internal-Token", "service-secret")
                        .header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"user_id\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error_code").value("invalid_arguments"));
    }

    @Test
    void malformedBodyNeverReachesTool() throws Exception {
        mvc.perform(post(URL).header("X-Internal-Token", "service-secret")
                        .header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void missingRunCredentialIsForbidden() throws Exception {
        when(service.execute(eq("run-1"), isNull(), eq("search_products"), anyMap()))
                .thenThrow(new ProRunRegistry.RegistryException(ProRunRegistry.Failure.FORBIDDEN));
        mvc.perform(post(URL).header("X-Internal-Token", "service-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error_code").value("invalid_run"));
    }

    @Test
    void registryOutageIsUnavailableRatherThanEmptyResult() throws Exception {
        when(service.execute(anyString(), anyString(), anyString(), anyMap()))
                .thenThrow(new ProRunRegistry.RegistryException(ProRunRegistry.Failure.UNAVAILABLE));
        mvc.perform(post(URL).header("X-Internal-Token", "service-secret")
                        .header("X-Pro-Run-Token", "run-secret")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("unavailable"))
                .andExpect(jsonPath("$.error_code").value("registry_unavailable"));
    }
}
