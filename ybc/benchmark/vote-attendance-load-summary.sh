#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_FILE="$(mktemp)"

cleanup() {
  rm -f "$OUTPUT_FILE"
}

trap cleanup EXIT

if ! (
  cd "$PROJECT_ROOT"
  bash ./gradlew voteAttendanceLoadSummary "$@"
) >"$OUTPUT_FILE" 2>&1; then
  cat "$OUTPUT_FILE"
  exit 1
fi

SUMMARY="$(
  awk '
    /^    Load Test$/ {
      capture = 1
    }

    capture {
      line = $0
      sub(/^    /, "", line)
      print line

      if (seen_p95 && line ~ /^[0-9]+ms$/) {
        exit
      }

      if (line == "P95") {
        seen_p95 = 1
      }
    }
  ' "$OUTPUT_FILE"
)"

if [[ -z "$SUMMARY" ]]; then
  cat "$OUTPUT_FILE"
  exit 1
fi

printf '%s\n' "$SUMMARY"
