#!/usr/bin/env bash
# Consolidates legacy attachment roots into the canonical one. Storage keys are
# yyyy-MM-dd/uuid.ext, so a plain move preserves every DB reference; UUID keys
# make collisions impossible. Run with the backend STOPPED.
set -euo pipefail
CANONICAL="${1:?usage: consolidate-attachments.sh <canonical-dir> [legacy-dir...]}"
shift
LEGACY=("${@:-data/attachments backend/data/attachments backend/app/data/attachments}")
mkdir -p "$CANONICAL"
for legacy in ${LEGACY[@]}; do
  [ -d "$legacy" ] || continue
  [ "$(cd "$legacy" && pwd -P)" = "$(cd "$CANONICAL" && pwd -P)" ] && continue
  echo "Merging $legacy -> $CANONICAL"
  (cd "$legacy" && find . -type f -print0) | while IFS= read -r -d '' f; do
    dest="$CANONICAL/${f#./}"
    mkdir -p "$(dirname "$dest")"
    if [ -e "$dest" ]; then echo "SKIP (exists): $f"; else mv "$legacy/$f" "$dest"; fi
  done
done
echo "Done. Verify with POST /api/admin/storage/verify (ADMIN)."
