from pathlib import Path
import unittest


PROJECT_DIR = Path(__file__).resolve().parents[2]


class GoldenPathConfigurationTests(unittest.TestCase):
    def test_integration_spec_does_not_intercept_browser_http(self) -> None:
        spec = (PROJECT_DIR / "frontend" / "e2e" / "integration" / "golden-path.spec.ts").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("page.route", spec)
        self.assertNotIn("routeFromHAR", spec)
        self.assertIn("/api/assistant/chat/stream", spec)
        self.assertIn("Idempotency-Key", spec)

    def test_cross_platform_runners_enable_integration_provider_and_clean_volumes(self) -> None:
        shell_runner = (PROJECT_DIR / "scripts" / "run-golden-path.sh").read_text(encoding="utf-8")
        windows_runner = (PROJECT_DIR / "scripts" / "run-golden-path.ps1").read_text(encoding="utf-8")

        for runner in (shell_runner, windows_runner):
            self.assertIn("AI_RUNTIME_ENV", runner)
            self.assertIn("AI_DETERMINISTIC_PROVIDER", runner)
            self.assertIn("MYSQL_HOST_PORT", runner)
            self.assertIn("JAVA_BACKEND_HOST_PORT", runner)
            self.assertIn("down", runner)
            self.assertIn("--volumes", runner)

    def test_demo_compose_rebuilds_a_non_empty_rag_index_before_frontend_starts(self) -> None:
        compose = (PROJECT_DIR / "docker-compose.demo.yml").read_text(encoding="utf-8")

        self.assertIn("rag-index-init:", compose)
        self.assertIn("rag-retrieval-check:", compose)
        self.assertIn("/internal/rag/rebuild", compose)
        self.assertIn('"chunkCount"', compose)
        self.assertIn('"source_count"', compose)
        self.assertIn("service_completed_successfully", compose)

    def test_ci_uploads_only_sanitized_golden_path_evidence(self) -> None:
        workflow = (PROJECT_DIR / ".github" / "workflows" / "cross-service.yml").read_text(
            encoding="utf-8"
        )
        shell_runner = (PROJECT_DIR / "scripts" / "run-golden-path.sh").read_text(
            encoding="utf-8"
        )
        windows_runner = (PROJECT_DIR / "scripts" / "run-golden-path.ps1").read_text(
            encoding="utf-8"
        )

        self.assertNotIn("frontend/test-results/integration/", workflow)
        self.assertIn("artifacts/golden-path/", workflow)
        for runner in (shell_runner, windows_runner):
            self.assertIn("playwright.log", runner)
            self.assertIn("sanitize_logs.py", runner)


if __name__ == "__main__":
    unittest.main()
