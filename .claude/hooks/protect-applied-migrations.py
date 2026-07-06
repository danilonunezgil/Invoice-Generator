#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

MIGRATION_DIR = "src/main/resources/db/migration"


def main():
    data = json.load(sys.stdin)
    file_path = data.get("tool_input", {}).get("file_path", "")
    normalized = file_path.replace("\\", "/")

    if not normalized.endswith(".sql") or f"/{MIGRATION_DIR}/" not in f"/{normalized}":
        return

    project_dir = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())
    try:
        rel_path = os.path.relpath(file_path, project_dir).replace("\\", "/")
    except ValueError:
        rel_path = normalized

    result = subprocess.run(
        ["git", "-C", project_dir, "ls-files", "--error-unmatch", "--", rel_path],
        capture_output=True,
    )
    if result.returncode != 0:
        return  # not tracked yet -> new migration, editing is fine

    match = re.match(r"^(V[0-9]+(?:\.[0-9]+)*)__", os.path.basename(file_path))
    version = match.group(1) if match else os.path.basename(file_path)

    reason = (
        f"La migracion {version} ya fue comprometida en git (Flyway no permite "
        "modificar migraciones ya aplicadas). Crea una nueva migracion con el "
        "siguiente numero de version en su lugar."
    )
    print(json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "deny",
            "permissionDecisionReason": reason,
        }
    }))


if __name__ == "__main__":
    main()
