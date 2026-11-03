#!/usr/bin/env bash
set -euo pipefail
: "${HY_SIGNING_DIR:?Runner private directory required}"
: "${GITHUB_RUN_ID:?Run ID required}"
: "${GITHUB_RUN_ATTEMPT:?Attempt required}"
umask 077
exchange="HY_WristNav/signing-exchange/${GITHUB_RUN_ID}-${GITHUB_RUN_ATTEMPT}.json"
# Only fetch data. Build source remains the checked-out, tested commit.
for attempt in $(seq 1 120); do
  git fetch --quiet origin main
  if git show "FETCH_HEAD:$exchange" > "$HY_SIGNING_DIR/envelope.json" 2>/dev/null; then
    node HY_WristNav/tools/unseal-signing.cjs "$HY_SIGNING_DIR/envelope.json" "$HY_SIGNING_DIR/runner-private.pem" "$HY_SIGNING_DIR/backup.tar.gz"
    tar -xzf "$HY_SIGNING_DIR/backup.tar.gz" -C "$HY_SIGNING_DIR" hy-wristnav.p12 password.txt
    test -s "$HY_SIGNING_DIR/hy-wristnav.p12"
    test -s "$HY_SIGNING_DIR/password.txt"
    echo "Original signing archive restored on the ephemeral runner."
    exit 0
  fi
  sleep 5
done
echo "::error::No encrypted signing archive supplied within 10 minutes; refusing to generate a replacement identity."
exit 1
