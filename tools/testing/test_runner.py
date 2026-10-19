"""Guard against false-positive test reports; uses only Python's standard library."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("runner", Path(__file__).with_name("run-gametests.py"))
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)


def log(selected=2, completed=2, passed=2):
    return (f"TACTICAL_TEST_SELECTION profile=FAST count={selected} ids=[a, b]\n"
            f"========= {completed} GAME TESTS COMPLETE ======================\n"
            f"All {passed} required tests passed :)\nBUILD SUCCESSFUL\n")


class ReportTests(unittest.TestCase):
    def test_actual_nonzero_success(self):
        self.assertEqual(runner.counts(log(), 0), (True, 2, 2))

    def test_zero_is_not_success(self):
        self.assertFalse(runner.counts(log(0, 0, 0), 0)[0])

    def test_no_source_is_not_success(self):
        self.assertFalse(runner.counts(":test NO-SOURCE\nBUILD SUCCESSFUL", 0)[0])

    def test_mismatched_selection_is_not_success(self):
        self.assertFalse(runner.counts(log(3, 2, 2), 0)[0])

    def test_missing_pass_is_not_success(self):
        self.assertFalse(runner.counts(log().replace("All 2 required tests passed", "1 required tests failed"), 0)[0])

    def test_gradle_failure_is_not_success(self):
        self.assertFalse(runner.counts(log(), 1)[0])

    def test_multiple_runs_are_not_one_receipt(self):
        self.assertFalse(runner.counts(log() + log(), 0)[0])


if __name__ == "__main__":
    unittest.main()
