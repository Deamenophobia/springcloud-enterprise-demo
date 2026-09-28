"""Guard against publishing a green summary from incomplete/failed test output."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("lab_report", Path(__file__).resolve().parents[1] / "lab-report.py")
reporter = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reporter)


class LabReportTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.lab = Path(self.temp.name)
        source = self.lab / "src/test/java/example/LessonTest.java"
        source.parent.mkdir(parents=True)
        source.write_text("", encoding="utf-8")
        self.report = self.lab / "target/surefire-reports/TEST-example.LessonTest.xml"
        self.report.parent.mkdir(parents=True)

    def write(self, body, **counts):
        attributes = {"tests": 1, "failures": 0, "errors": 0, "skipped": 0, **counts}
        text = " ".join(f'{key}="{value}"' for key, value in attributes.items())
        self.report.write_text(f'<testsuite name="example.LessonTest" {text}>{body}</testsuite>', encoding="utf-8")

    def test_success_exports_only_named_test_outcomes(self):
        self.write('<properties><property name="secret" value="DO_NOT_EXPORT"/></properties>'
                   '<testcase name="works"/><system-out>PRIVATE_LOG</system-out>')
        result = reporter.collect(self.lab)
        self.assertTrue(result["successful"])
        self.assertEqual(result["counts"]["PASS"], 1)
        self.assertNotIn("DO_NOT_EXPORT", str(result))
        self.assertNotIn("PRIVATE_LOG", str(result))

    def test_missing_suite_is_not_success(self):
        self.assertFalse(reporter.collect(self.lab)["successful"])

    def test_empty_suite_is_not_success(self):
        self.write("", tests=0)
        self.assertFalse(reporter.collect(self.lab)["successful"])

    def test_failure_error_and_skip_are_not_success(self):
        for tag, counter, status in (("failure", "failures", "FAIL"), ("error", "errors", "ERROR"), ("skipped", "skipped", "SKIP")):
            with self.subTest(tag=tag):
                self.write(f'<testcase name="works"><{tag}/></testcase>', **{counter: 1})
                result = reporter.collect(self.lab)
                self.assertFalse(result["successful"])
                self.assertEqual(result["counts"][status], 1)

    def test_inconsistent_summary_is_not_success(self):
        self.write('<testcase name="works"/>', tests=2)
        self.assertFalse(reporter.collect(self.lab)["successful"])


if __name__ == "__main__":
    unittest.main()
