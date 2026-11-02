from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from coverage_summary import summarize


class CoverageSummaryTest(unittest.TestCase):
    def report(self, body):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "jacoco.xml"
            path.write_text(body, encoding="utf-8")
            return summarize(path)

    def test_uses_root_totals_once_and_keeps_uncovered_sources(self):
        result = self.report('''<report>
          <package name="com/onze">
            <sourcefile name="Untested.java">
              <counter type="LINE" missed="2" covered="0"/>
              <counter type="BRANCH" missed="0" covered="0"/>
            </sourcefile>
            <counter type="LINE" missed="2" covered="3"/>
          </package>
          <counter type="LINE" missed="2" covered="3"/>
          <counter type="BRANCH" missed="3" covered="1"/>
          <counter type="INSTRUCTION" missed="4" covered="6"/>
        </report>''')
        self.assertEqual(result["metrics"]["line"]["total"], 5)
        self.assertEqual(result["metrics"]["line"]["pct"], 60)
        self.assertEqual(result["metrics"]["branch"]["pct"], 25)
        self.assertEqual(result["files"][0]["metrics"]["line"]["pct"], 0)
        self.assertIsNone(result["files"][0]["metrics"]["branch"]["pct"])

    def test_rejects_missing_measurement(self):
        with self.assertRaises(ValueError):
            self.report('<report><counter type="LINE" missed="0" covered="0"/></report>')

    def test_missing_report_is_not_presented_as_zero_or_full_coverage(self):
        with self.assertRaises(FileNotFoundError):
            summarize(Path("does-not-exist/jacoco.xml"))
