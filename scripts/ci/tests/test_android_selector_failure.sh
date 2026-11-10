#!/usr/bin/env bash
# Execute the actual CI test block: selector errors must never become green jobs.
set -euo pipefail
script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root_dir="$(cd "$script_dir/../../.." && pwd)"
python3 - "$root_dir" <<'PYTEST'
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(sys.argv[1])
lines = (root / ".gitlab-ci.yml").read_text().splitlines()
start = lines.index(".xinyi_android_test:")
block_start = next(i for i in range(start, len(lines)) if lines[i] == "    - |") + 1
block = []
for line in lines[block_start:]:
    if line and not line.startswith("      "):
        break
    block.append(line[6:])
script = "\n".join(block)
with tempfile.TemporaryDirectory(prefix="selector-ci-") as directory:
    fixture = Path(directory)
    toolkit = fixture / "toolkit"
    (toolkit / "ci").mkdir(parents=True)
    (toolkit / "gradle").mkdir()
    marker = fixture / "runner-args"
    (toolkit / "gradle/run_gradle_with_retry.sh").write_text(
        '#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$SELECTOR_TEST_MARKER"\n'
    )
    env = dict(os.environ, CI_PROJECT_DIR=str(root), MAGISK_CI_TOOLKIT_DIR=str(toolkit),
               SELECTOR_TEST_MARKER=str(marker))
    for name, selector, success, expected_tasks in (
        ("selector failure", "import sys; sys.exit(23)\n", False, []),
        ("empty selection", "pass\n", True, []),
        ("selected tasks", 'print(":app:testGithubDebugUnitTest"); print("verifyModuleBoundaries")\n',
         True, [":app:testGithubDebugUnitTest", "verifyModuleBoundaries"]),
    ):
        marker.unlink(missing_ok=True)
        (toolkit / "ci/select_gradle_tasks.py").write_text(selector)
        result = subprocess.run(["bash", "-c", script], env=env, text=True, capture_output=True)
        assert (result.returncode == 0) == success, (name, result.returncode, result.stdout, result.stderr)
        if expected_tasks:
            actual = marker.read_text().splitlines()
            for task in expected_tasks:
                assert task in actual, (name, actual)
        else:
            assert not marker.exists(), (name, "Gradle runner unexpectedly invoked")
        print("ok - " + name)
PYTEST
