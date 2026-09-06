#!/usr/bin/env bash
# Commit-and-push helper that enforces the project workflow rules:
#   * every commit has a subject (name) AND a body (description)   → becomes the GitHub Release title/notes
#   * nothing generated or secret is staged (build/, .gradle/, local.properties, keystores)
#   * the push goes to origin/main
#
# Usage:
#   scripts/push.sh "feat: subject line" "multi-line body"          # body as 2nd arg
#   scripts/push.sh "feat: subject line" < notes.md                 # body from stdin
#   scripts/push.sh -F message.txt                                  # full message from file (subject + blank line + body)
set -euo pipefail
cd "$(dirname "$0")/.."

msg_file=$(mktemp)
trap 'rm -f "$msg_file"' EXIT

if [[ "${1:-}" == "-F" ]]; then
  cp "${2:?message file}" "$msg_file"
else
  subject="${1:?usage: push.sh \"subject\" [\"body\"] | -F file}"
  if [[ $# -ge 2 ]]; then body="$2"; elif [[ ! -t 0 ]]; then body="$(cat)"; else body=""; fi
  printf '%s\n\n%s\n' "$subject" "$body" > "$msg_file"
fi

subject_line=$(sed -n '1p' "$msg_file")
body_text=$(sed '1,2d' "$msg_file" | sed '/^[[:space:]]*$/d')
if [[ -z "$subject_line" ]]; then echo "error: empty commit subject" >&2; exit 1; fi
if [[ -z "$body_text" ]]; then echo "error: commit body/description is required (it becomes the release notes)" >&2; exit 1; fi
if [[ ${#subject_line} -gt 72 ]]; then echo "warning: subject longer than 72 chars" >&2; fi

# Safety net: never commit local/generated files even if .gitignore was edited.
forbidden='(^|/)(local\.properties|.*\.jks|.*\.keystore|keystore\.properties|google-services\.json)$|(^|/)(build|\.gradle|\.kotlin|\.idea)/'
git add -A
if git diff --cached --name-only | grep -Eq "$forbidden"; then
  echo "error: refusing to commit generated/secret files:" >&2
  git diff --cached --name-only | grep -E "$forbidden" >&2
  exit 1
fi

if git diff --cached --quiet; then
  echo "nothing to commit – pushing existing commits only"
else
  git commit -q -F "$msg_file"
  echo "committed: $(git log -1 --pretty='%h %s')"
fi

branch=$(git rev-parse --abbrev-ref HEAD)
git push -u origin "$branch"
echo "pushed $branch → origin ($(git rev-parse --short HEAD))"
