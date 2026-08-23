#!/usr/bin/env bash
# Manual on-demand backup of both the PostgreSQL database and the media_data volume
# (product images + private expense receipts) for the QR Code Menu stack. Runs pg_dump
# inside the running "postgres" container and tars media_data via the running
# "media-backup" container (both via `docker compose exec`) - no local postgres client
# needed. Complements the automatic postgres-backup/media-backup sidecars; use this
# before a risky migration/deploy or to grab an extra copy.
#
# Usage (from anywhere - resolves paths relative to this script):
#   ./scripts/backup.sh          # dev stack  (docker-compose.yml,      infra/.env)
#   ./scripts/backup.sh prod     # prod stack (docker-compose.prod.yml, infra/.env.prod)
#
# Output: infra/backups/<db>_manual_<UTC timestamp>.sql.gz
#         infra/backups/media/media_manual_<UTC timestamp>.tar.gz
set -euo pipefail

ENV_NAME="${1:-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"
cd "${INFRA_DIR}"

if [ "${ENV_NAME}" = "prod" ]; then
  ENV_FILE=".env.prod"
  COMPOSE_ARGS=(--env-file "${ENV_FILE}" -f docker-compose.prod.yml)
elif [ "${ENV_NAME}" = "dev" ]; then
  ENV_FILE=".env"
  COMPOSE_ARGS=(-f docker-compose.yml)
else
  echo "Unknown target '${ENV_NAME}', expected 'dev' or 'prod'." >&2
  exit 1
fi

if [ ! -f "${ENV_FILE}" ]; then
  echo "Missing infra/${ENV_FILE} - copy it from ${ENV_FILE}.example first." >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

POSTGRES_DB="${POSTGRES_DB:-qrmenu}"
POSTGRES_USER="${POSTGRES_USER:-qrmenu}"

mkdir -p backups
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="backups/${POSTGRES_DB}_manual_${TIMESTAMP}.sql.gz"
TMP_FILE="${OUT_FILE}.tmp"

echo "Backing up '${POSTGRES_DB}' (${ENV_NAME}) -> ${OUT_FILE}"
docker compose "${COMPOSE_ARGS[@]}" exec -T postgres \
  pg_dump -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" --format=plain --no-owner --no-privileges \
  | gzip > "${TMP_FILE}"
mv "${TMP_FILE}" "${OUT_FILE}"
echo "Done: ${OUT_FILE} ($(du -h "${OUT_FILE}" | cut -f1))"

mkdir -p backups/media
MEDIA_OUT_FILE="backups/media/media_manual_${TIMESTAMP}.tar.gz"
MEDIA_TMP_FILE="${MEDIA_OUT_FILE}.tmp"

echo "Backing up media_data volume (${ENV_NAME}) -> ${MEDIA_OUT_FILE}"
docker compose "${COMPOSE_ARGS[@]}" exec -T media-backup \
  tar czf - -C /media . \
  > "${MEDIA_TMP_FILE}"
mv "${MEDIA_TMP_FILE}" "${MEDIA_OUT_FILE}"
echo "Done: ${MEDIA_OUT_FILE} ($(du -h "${MEDIA_OUT_FILE}" | cut -f1))"
