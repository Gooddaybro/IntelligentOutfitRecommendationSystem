from pathlib import Path
import unittest


PROJECT_DIR = Path(__file__).resolve().parents[2]


class DemoConfigurationTests(unittest.TestCase):
    def test_default_python_context_matches_the_versioned_sibling_name(self) -> None:
        compose = (PROJECT_DIR / "docker-compose.demo.yml").read_text(encoding="utf-8")
        env_example = (PROJECT_DIR / ".env.demo.example").read_text(encoding="utf-8")
        mirror_env_example = (PROJECT_DIR / ".env.daocloud.example").read_text(encoding="utf-8")

        expected = "../AI Clothing Shopping Assistant System"
        self.assertIn(expected, compose)
        self.assertIn(f"PYTHON_AI_CONTEXT={expected}", env_example)
        self.assertIn(f"PYTHON_AI_CONTEXT={expected}", mirror_env_example)

    def test_start_script_accepts_both_supported_python_repository_names(self) -> None:
        script = (PROJECT_DIR / "scripts" / "start-demo.sh").read_text(encoding="utf-8")
        stop_script = (PROJECT_DIR / "scripts" / "stop-demo.sh").read_text(encoding="utf-8")

        self.assertIn("AI Clothing Shopping Assistant System", script)
        self.assertIn("AI-Clothing-Shopping-Assistant-System", script)
        self.assertIn("AI Clothing Shopping Assistant System", stop_script)

    def test_nginx_disables_buffering_for_assistant_sse_and_has_health_endpoint(self) -> None:
        nginx = (PROJECT_DIR / "frontend" / "nginx.conf").read_text(encoding="utf-8")
        dockerfile = (PROJECT_DIR / "frontend" / "Dockerfile").read_text(encoding="utf-8")

        self.assertIn("location = /api/assistant/chat/stream", nginx)
        self.assertIn("proxy_buffering off;", nginx)
        self.assertIn("proxy_read_timeout 300s;", nginx)
        self.assertIn("location = /healthz", nginx)
        self.assertIn("http://127.0.0.1/healthz", dockerfile)

    def test_windows_compose_verifier_is_available(self) -> None:
        verifier = PROJECT_DIR / "scripts" / "verify-compose.ps1"

        self.assertTrue(verifier.is_file())


if __name__ == "__main__":
    unittest.main()
