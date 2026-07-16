package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.RestDemandIntentParseClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestDemandIntentParseClientTests {

    private HttpServer server;

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void postsInternalTokenAndParsesStrictCandidate() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/demand-intent/parse", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {"schemaVersion":"1.0","action":"MERGE","slots":{"targetGender":"FEMALE"},
                    "slotConfidence":{"targetGender":0.93},"evidence":{"targetGender":[{"text":"女性","source":"CURRENT_MESSAGE"}]},
                    "needsClarification":false}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        var client = new RestDemandIntentParseClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), 1000, "secret");
        var request = new LlmDemandParseRequest("req-1", "thread-1", "女性穿搭",
                new DemandIntentPatch("merge", "女性穿搭", null, false, null,
                        List.of(), List.of(), null, List.of()),
                List.of(), List.of(), "女性", List.of(), null);

        var response = client.parse(request).orElseThrow();

        assertThat(token.get()).isEqualTo("secret");
        assertThat(body.get()).contains("\"currentMessage\":\"女性穿搭\"");
        assertThat(response.slots().targetGender()).isEqualTo("FEMALE");
    }
}
