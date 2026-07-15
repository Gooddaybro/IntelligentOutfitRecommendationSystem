package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.HttpRagRebuildClient;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRagRebuildClientTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsInternalTokenAndCorrelationHeaders() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> traceparent = new AtomicReference<>();
        AtomicReference<String> upgrade = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/rag/rebuild", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            traceparent.set(exchange.getRequestHeaders().getFirst("traceparent"));
            upgrade.set(exchange.getRequestHeaders().getFirst("Upgrade"));
            byte[] body = """
                    {"taskId":"task-one","indexVersion":"v1","fileCount":2,
                     "chunkCount":4,"contentDigest":"abc","replayed":false}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        HttpRagRebuildClient client = new HttpRagRebuildClient(
                "http://localhost:" + server.getAddress().getPort(), 1000, 3000, "internal-secret"
        );

        RagRebuildResult result = client.rebuild("task-one", "request-one", "trace-one");

        assertThat(result.taskId()).isEqualTo("task-one");
        assertThat(token).hasValue("internal-secret");
        assertThat(requestId).hasValue("request-one");
        assertThat(traceparent).hasValue("trace-one");
        assertThat(upgrade).hasNullValue();
    }
}
