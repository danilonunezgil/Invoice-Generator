#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

CONFIDENTIAL_PREFIX = "docs/reference/"
ALLOWED_EXACT = {"docs/reference/README.md"}
ALLOWED_PREFIX = "docs/reference/public/"


def is_allowed(path):
    return path in ALLOWED_EXACT or path.startswith(ALLOWED_PREFIX)


def main():
    data = json.load(sys.stdin)
    command = data.get("tool_input", {}).get("command", "")

    if not re.search(r"\bgit\s+commit\b", command):
        return

    project_dir = os.environ.get("CLAUDE_PROJECT_DIR", os.getcwd())
    result = subprocess.run(
        ["git", "-C", project_dir, "diff", "--cached", "--name-only"],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        return

    leaked = [
        p for p in result.stdout.splitlines()
        if p.startswith(CONFIDENTIAL_PREFIX) and not is_allowed(p)
    ]
    if not leaked:
        return

    reason = (
        "Bloqueado: el commit incluye archivo(s) confidenciales de "
        "docs/reference/ que no deben publicarse: "
        f"{', '.join(leaked)}. Si es intencional, revisa .gitignore o "
        "el hook protect-confidential-reference.py."
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
