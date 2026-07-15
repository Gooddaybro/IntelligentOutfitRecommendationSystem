package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"test", "web", "worker"})
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@EnabledIfEnvironmentVariable(named = "RUN_RABBITMQ_E2E", matches = "true")
class RagRebuildEndToEndTests {

    private static final String INTERNAL_TOKEN = "rag-e2e-internal-token";
    private static final int PYTHON_PORT = freePort();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
            .withDatabaseName("intelligent_outfit")
            .withUsername("app")
            .withPassword("app-password");

    @Container
    static final RabbitMQContainer RABBITMQ =
            new RabbitMQContainer("rabbitmq:4.1.8-management");

    private static Process pythonProcess;
    private static Path pythonWorkDirectory;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AiTaskMapper taskMapper;

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
        registry.add("app.ai-task.publisher-enabled", () -> "true");
        registry.add("app.ai-task.listener-enabled", () -> "true");
        registry.add("app.ai-task.publisher-fixed-delay-ms", () -> "100");
        registry.add("app.ai.python-base-url", () -> "http://127.0.0.1:" + PYTHON_PORT);
        registry.add("app.ai.python-internal-token", () -> INTERNAL_TOKEN);
    }

    @BeforeAll
    static void startPythonProcess() throws Exception {
        pythonWorkDirectory = Files.createTempDirectory("rag-rebuild-e2e-");
        Path pythonProject = pythonProjectDirectory();
        Path launcher = writePythonLauncher(pythonWorkDirectory);
        Path log = pythonWorkDirectory.resolve("python.log");
        ProcessBuilder processBuilder = new ProcessBuilder(
                pythonExecutable(),
                launcher.toString()
        ).directory(pythonProject.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        processBuilder.environment().put("APP_INTERNAL_API_TOKEN", INTERNAL_TOKEN);
        processBuilder.environment().put("RAG_E2E_PORT", Integer.toString(PYTHON_PORT));
        processBuilder.environment().put("RAG_E2E_WORK_DIR", pythonWorkDirectory.toString());
        processBuilder.environment().put("PYTHONPATH", pythonProject.toString());
        pythonProcess = processBuilder.start();
        waitForPython(log);
    }

    @AfterAll
    static void stopPythonProcess() throws Exception {
        if (pythonProcess != null) {
            pythonProcess.destroy();
            if (!pythonProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                pythonProcess.destroyForcibly();
                pythonProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
        }
        if (pythonWorkDirectory != null) {
            try {
                deleteTree(pythonWorkDirectory);
            } catch (IOException ignored) {
                // Windows may retain the redirected log handle briefly; the OS temp directory is safe to reap later.
            }
        }
    }

    @BeforeEach
    void cleanTaskTables() {
        jdbcTemplate.update("DELETE FROM ai_task_redrive_audit");
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        jdbcTemplate.update("DELETE FROM outbox_event");
        jdbcTemplate.update("DELETE FROM ai_task");
    }

    @Test
    void administratorCompletesRealRabbitAndPythonRebuildLoop() throws Exception {
        mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("2"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andExpect(status().isForbidden());

        String body = mockMvc.perform(post("/api/ai/tasks")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("X-Request-Id", "rag-e2e-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskType\":\"RAG_REBUILD\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String taskId = OBJECT_MAPPER.readTree(body).path("data").path("taskId").asText();

        AiTask completed = waitForSuccess(taskId);
        JsonNode result = OBJECT_MAPPER.readTree(completed.getResultJson());
        JsonNode health = getPythonJson("/health/rag");

        assertThat(result.path("taskId").asText()).isEqualTo(taskId);
        assertThat(health.path("ready").asBoolean()).isTrue();
        assertThat(health.path("source_task_id").asText()).isEqualTo(taskId);
        assertThat(health.path("version").asText()).isEqualTo(result.path("indexVersion").asText());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consumer_inbox WHERE task_id = ?",
                Integer.class,
                taskId
        )).isOne();
        System.out.printf(
                "RAG_E2E_EVIDENCE taskId=%s indexVersion=%s chunkCount=%d%n",
                taskId,
                health.path("version").asText(),
                health.path("chunk_count").asInt()
        );
    }

    private AiTask waitForSuccess(String taskId) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        AiTask task;
        do {
            task = taskMapper.findByTaskId(taskId);
            if (task != null && "SUCCESS".equals(task.getStatus())) {
                return task;
            }
            if (task != null && "FAILED".equals(task.getStatus())) {
                throw new AssertionError("RAG rebuild failed: " + task.getFailureSummary());
            }
            Thread.sleep(100);
        } while (Instant.now().isBefore(deadline));
        throw new AssertionError("RAG rebuild did not reach SUCCESS; last task=" + task);
    }

    private static JsonNode getPythonJson(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + PYTHON_PORT + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static Path pythonProjectDirectory() {
        String configured = System.getenv("PYTHON_PROJECT_DIR");
        Path path = configured == null || configured.isBlank()
                ? Path.of("..", "..", "AI Clothing Shopping Assistant System")
                : Path.of(configured);
        Path absolute = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute.resolve("clothing_assistant"))) {
            throw new IllegalStateException("Python project not found: " + absolute);
        }
        return absolute;
    }

    private static String pythonExecutable() {
        String configured = System.getenv("PYTHON_EXECUTABLE");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        return "python";
    }

    private static Path writePythonLauncher(Path workDirectory) throws IOException {
        Path launcher = workDirectory.resolve("launch_e2e.py");
        String script = """
                import os
                from pathlib import Path
                import uvicorn
                import clothing_assistant.infrastructure.vector_store as store

                class DeterministicEmbeddings:
                    def embed_documents(self, texts):
                        return [self._embed(text) for text in texts]

                    def embed_query(self, text):
                        return self._embed(text)

                    @staticmethod
                    def _embed(text):
                        encoded = text.encode("utf-8")
                        return [float(len(encoded)), float(sum(encoded) % 997), 1.0]

                root = Path(os.environ["RAG_E2E_WORK_DIR"])
                store.VECTOR_DB_DIR = root
                store.VECTOR_STORE_FILE = root / "simple_vector_store.json"
                store.VECTOR_STORE_META_FILE = root / "vector_store_meta.json"
                store.VECTOR_STORE_POINTER_FILE = root / "current.json"
                store.VECTOR_STORE_VERSIONS_DIR = root / "versions"
                store._EMBEDDINGS_CACHE = DeterministicEmbeddings()

                from clothing_assistant.api.app import app
                uvicorn.run(app, host="127.0.0.1", port=int(os.environ["RAG_E2E_PORT"]), log_level="warning")
                """;
        Files.writeString(launcher, script, StandardCharsets.UTF_8);
        return launcher;
    }

    private static void waitForPython(Path log) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            if (!pythonProcess.isAlive()) {
                throw new AssertionError("Python process stopped:\n" + Files.readString(log));
            }
            try {
                getPythonJson("/health");
                return;
            } catch (Exception ignored) {
                Thread.sleep(100);
            }
        }
        throw new AssertionError("Python process did not become ready:\n" + Files.readString(log));
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot allocate Python E2E port", exception);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
