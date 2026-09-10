import unittest
from ci_changes import docs_only


class ChangeClassificationTests(unittest.TestCase):
    def test_docs_and_word_only(self):
        self.assertTrue(docs_only(['README.md', 'docs/开发说明.docx']))

    def test_code_mixed_with_docs_runs_verification(self):
        self.assertFalse(docs_only(['docs/说明.docx', 'backend/pom.xml']))

    def test_contract_workflow_scripts_and_resources_are_not_docs(self):
        for path in ['.github/workflows/ci.yml', 'scripts/ci_changes.py',
                     'backend/src/test/resources/contracts/v1.fields.json',
                     'backend/src/main/resources/prompt.md', 'docs/tool.py']:
            self.assertFalse(docs_only([path]), path)

    def test_unknown_or_empty_runs_verification(self):
        self.assertFalse(docs_only([]))
        self.assertFalse(docs_only(['unknown.file']))


if __name__ == '__main__':
    unittest.main()
