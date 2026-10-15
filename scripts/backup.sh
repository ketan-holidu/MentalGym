#!/usr/bin/env bash
# Dumps the Mental Gym database into ./backups with a timestamped filename.
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p backups
stamp="$(date +%Y%m%d-%H%M%S)"
out="backups/mental-gym-${stamp}.sql"

docker compose exec -T db pg_dump -U mentalgym mentalgym > "$out"
echo "wrote $out"
