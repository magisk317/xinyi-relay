import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import manage_dependabot_alerts as mda


def rust_alert(package: str, version_range: str, patched: str, number: int = 1) -> dict:
    return {
        "number": number,
        "state": "open",
        "dependency": {
            "manifest_path": "desktop/src-tauri/Cargo.lock",
            "package": {
                "ecosystem": "rust",
                "name": package,
            },
        },
        "security_vulnerability": {
            "vulnerable_version_range": version_range,
            "first_patched_version": {
                "identifier": patched,
            },
        },
    }


class ManageDependabotAlertsTest(unittest.TestCase):
    def test_version_in_range_handles_common_dependabot_ranges(self):
        self.assertTrue(mda.version_in_range("0.8.5", ">= 0.7.0, < 0.8.6"))
        self.assertFalse(mda.version_in_range("0.8.6", ">= 0.7.0, < 0.8.6"))
        self.assertTrue(mda.version_in_range("4.1.132.Final", "<= 4.1.132.Final"))
        self.assertFalse(mda.version_in_range("4.1.133.Final", "<= 4.1.132.Final"))

    def test_collect_cargo_update_plan_uses_locked_vulnerable_versions(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest_dir = root / "desktop" / "src-tauri"
            manifest_dir.mkdir(parents=True)
            (manifest_dir / "Cargo.toml").write_text("[package]\nname = \"demo\"\nversion = \"0.1.0\"\n")
            (manifest_dir / "Cargo.lock").write_text(
                """
[[package]]
name = "rand"
version = "0.7.3"

[[package]]
name = "rand"
version = "0.8.5"

[[package]]
name = "rand"
version = "0.9.3"
""".lstrip()
            )

            plan = mda.collect_cargo_update_plan(
                root,
                [rust_alert("rand", ">= 0.7.0, < 0.8.6", "0.8.6")],
            )

            planned = [(item.package, item.current, item.target, item.status) for item in plan]
            self.assertEqual(
                [
                    ("rand", "0.7.3", "0.8.6", "planned"),
                    ("rand", "0.8.5", "0.8.6", "planned"),
                ],
                planned,
            )


if __name__ == "__main__":
    unittest.main()
