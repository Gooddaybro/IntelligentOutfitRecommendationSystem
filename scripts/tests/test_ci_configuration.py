from pathlib import Path
import re
import unittest


PROJECT_DIR = Path(__file__).resolve().parents[2]


class CiConfigurationTests(unittest.TestCase):
    def test_pull_request_ci_is_split_into_independent_quality_gates(self) -> None:
        workflow = (PROJECT_DIR / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")

        self.assertIn("concurrency:", workflow)
        for job in (
            "shared-contract:",
            "backend-verify:",
            "python-quality-and-tests:",
            "frontend-test-and-build:",
            "container-config-and-build:",
        ):
            self.assertIn(job, workflow)
        self.assertIn("timeout-minutes:", workflow)
        self.assertIn("cache:", workflow)

    def test_cross_service_workflow_pins_python_and_runs_three_clean_paths(self) -> None:
        workflow_path = PROJECT_DIR / ".github" / "workflows" / "cross-service.yml"
        revision_path = PROJECT_DIR / ".services" / "python-revision"

        self.assertTrue(workflow_path.is_file())
        self.assertTrue(revision_path.is_file())
        workflow = workflow_path.read_text(encoding="utf-8")
        revision = revision_path.read_text(encoding="utf-8").strip()
        self.assertRegex(revision, re.compile(r"^[0-9a-f]{40}$"))
        self.assertIn("schedule:", workflow)
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("for run in 1 2 3", workflow)
        self.assertIn("actions/upload-artifact", workflow)
        self.assertIn(".services/python-revision", workflow)

    def test_canonical_contract_snapshot_is_versioned_with_integration_repo(self) -> None:
        contracts = PROJECT_DIR / "contracts"

        self.assertTrue((contracts / "java-python-chat" / "v1.fields.json").is_file())
        self.assertTrue(
            (contracts / "rag-rebuild" / "schemas" / "rag-rebuild-request.schema.json").is_file()
        )


if __name__ == "__main__":
    unittest.main()
