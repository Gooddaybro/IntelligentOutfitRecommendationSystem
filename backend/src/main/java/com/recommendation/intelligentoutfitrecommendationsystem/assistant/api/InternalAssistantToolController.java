package com.recommendation.intelligentoutfitrecommendationsystem.assistant.api;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProToolResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProToolQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-only v2 tool gateway behind the existing internal-service token interceptor.
 * A separate run credential binds every query to a Java-created execution ledger.
 * This controller does not expose creation of runs or any commerce mutations.
 */
@RestController
@RequestMapping("/internal/assistant/runs/{runId}/tools")
public class InternalAssistantToolController {
    private final ProToolQueryService service;

    public InternalAssistantToolController(ProToolQueryService service) {
        this.service = service;
    }

    /** Receives only tool arguments; authorization is checked before catalog access. */
    @PostMapping("/{toolName}")
    public ProToolResult execute(@PathVariable String runId, @PathVariable String toolName,
                                 @RequestHeader(value = "X-Pro-Run-Token", required = false) String token,
                                 @RequestBody Map<String, Object> arguments) {
        return service.execute(runId, token, toolName, arguments);
    }

    /** Keeps registry outages distinct from invalid credentials without exposing diagnostics. */
    @ExceptionHandler(ProRunRegistry.RegistryException.class)
    public ResponseEntity<ProToolResult> registryFailure(ProRunRegistry.RegistryException error) {
        return switch (error.failure()) {
            case FORBIDDEN -> ResponseEntity.status(403)
                    .body(ProToolResult.of("forbidden", null, "invalid_run"));
            case CAPACITY -> ResponseEntity.status(409)
                    .body(ProToolResult.of("unavailable", null, "run_capacity_exceeded"));
            case UNAVAILABLE -> ResponseEntity.status(503)
                    .body(ProToolResult.of("unavailable", null, "registry_unavailable"));
        };
    }

    /** Invalid arguments never fall through as apparent missing products or zero inventory. */
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProToolResult> invalidArguments(BadRequestException error) {
        return ResponseEntity.badRequest().body(ProToolResult.of("forbidden", null, "invalid_arguments"));
    }
}
