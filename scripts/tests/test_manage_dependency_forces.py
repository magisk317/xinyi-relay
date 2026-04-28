import sys
import unittest
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import manage_dependency_forces as mdf


def alert(
    dep: str,
    state: str,
    *,
    created_at: str = "2026-04-20T00:00:00Z",
    updated_at: str = "2026-04-20T00:00:00Z",
    fixed_at: str | None = None,
    dismissed_at: str | None = None,
    patched: str | None = None,
) -> dict:
    first_patched_version = {}
    if patched is not None:
        first_patched_version["identifier"] = patched
    return {
        "state": state,
        "created_at": created_at,
        "updated_at": updated_at,
        "fixed_at": fixed_at,
        "dismissed_at": dismissed_at,
        "dependency": {
            "package": {
                "ecosystem": "maven",
                "name": dep,
            }
        },
        "security_vulnerability": {
            "first_patched_version": first_patched_version,
        },
    }


class ManageDependencyForcesTest(unittest.TestCase):
    def test_version_in_range_returns_none_for_empty_range(self):
        self.assertIsNone(mdf._version_in_range("4.1.132.Final", ""))
        self.assertIsNone(mdf._version_in_range("4.1.132.Final", " , , "))

    def test_is_safe_to_remove_rejects_open_alert(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[alert("io.netty:netty-codec-http", "open", patched="4.1.125.Final")],
            cooldown_hours=168,
            check_advisories=False,
        )
        self.assertFalse(result)

    def test_open_alert_filter_only_keeps_open_alerts_for_force_additions(self):
        alerts = [
            alert("io.netty:netty-codec-http", "open", patched="4.1.125.Final"),
            alert("io.netty:netty-codec-http", "fixed", patched="4.1.125.Final"),
        ]

        open_alerts = [item for item in alerts if mdf._is_open_alert(item)]

        self.assertEqual(1, len(open_alerts))
        self.assertEqual("open", open_alerts[0]["state"])

    def test_is_safe_to_remove_rejects_recent_historical_alert(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-27T04:15:39Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertFalse(result)

    def test_is_safe_to_remove_allows_old_historical_alert_after_cooldown(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.132.Final",
            forced_version="4.1.125.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-01T00:00:00Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertTrue(result)

    def test_is_safe_to_remove_rejects_below_patched_baseline(self):
        result = mdf.is_safe_to_remove(
            dep="io.netty:netty-codec-http",
            natural_version="4.1.124.Final",
            forced_version="4.1.124.Final",
            alerts=[
                alert(
                    "io.netty:netty-codec-http",
                    "fixed",
                    fixed_at="2026-04-01T00:00:00Z",
                    patched="4.1.125.Final",
                )
            ],
            cooldown_hours=168,
            check_advisories=False,
            now=datetime(2026, 4, 28, 0, 0, tzinfo=timezone.utc),
        )
        self.assertFalse(result)


if __name__ == "__main__":
    unittest.main()
