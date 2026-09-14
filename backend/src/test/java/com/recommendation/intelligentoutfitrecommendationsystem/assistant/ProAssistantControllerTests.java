package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.api.ProAssistantController;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProAssistantControllerTests {
    private final ProAssistantService service = mock(ProAssistantService.class);
    private final JwtAuthenticationToken user = new JwtAuthenticationToken(Jwt.withTokenValue("test")
            .header("alg", "none").subject("7").build());
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ProAssistantController(service)).build();
    }

    @Test
    void disabledReturnsExplicitUnavailable() throws Exception {
        when(service.exchangeForValidation(eq(7L), any()))
                .thenThrow(new ProAssistantService.ProUnavailableException("pro_disabled"));
        mvc.perform(post("/api/assistant/v2/chat").principal(user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"jacket\",\"agentMode\":\"pro\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("errorCode").value("pro_disabled"));
    }

    @Test
    void trustedFieldsCannotReplaceIdentityAndUnvalidatedAnswerNeverEscapes() throws Exception {
        when(service.exchangeForValidation(eq(7L), any()))
                .thenReturn(new ObjectMapper().readTree("{\"answer\":\"unsafe answer\",\"tool_run_token\":\"secret\"}"));
        var response = mvc.perform(post("/api/assistant/v2/chat").principal(user).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"jacket","agentMode":"pro","userId":999,
                                 "user_context":{"user_id":999},"candidates":[{"spu_id":999}],
                                 "tool_run_token":"forged","budgetMax":99.90}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("errorCode").value("pro_result_not_ready")).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("unsafe answer", "secret", "forged");
        var captured = ArgumentCaptor.forClass(ProChatRequest.class);
        verify(service).exchangeForValidation(eq(7L), captured.capture());
        assertThat(captured.getValue().budgetMax()).isEqualByComparingTo("99.90");
    }

    @Test
    void invalidModeAndNegativeBudgetAreRejectedBeforeService() throws Exception {
        for (String body : new String[]{"{\"message\":\"jacket\",\"agentMode\":\"lite\"}",
                "{\"message\":\"jacket\",\"agentMode\":\"pro\",\"budgetMax\":-1}"}) {
            mvc.perform(post("/api/assistant/v2/chat").principal(user).contentType(MediaType.APPLICATION_JSON)
                            .content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
}
