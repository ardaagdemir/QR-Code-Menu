#!/usr/bin/env bash
# Restores a media_data archive produced by backup.sh or the media-backup sidecar
# (product images + private expense receipts).
#
# DESTRUCTIVE: wipes everything currently in the media_data volume, then extracts the
# archive into it - anything uploaded after the backup was taken is lost. Requires
# typing a fixed confirmation phrase (not just Enter/y) to proceed - same discipline as
# restore.sh, deliberately a stricter prompt here since there's no single "database
# name" to type back for a volume.
#
# Usage (from anywhere - resolves paths relative to this script):
#   ./scripts/restore-media.sh <path-to-media-backup.tar.gz> [dev|prod]
#
# Example:
#   ./scripts/restore-media.sh backups/media/media_manual_20260814T120000Z.tar.gz dev
set -euo pipefail

BACKUP_FILE="${1:?Usage: restore-media.sh <media-backup.tar.gz> [dev|prod]}"
ENV_NAME="${2:-dev}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"

case "${BACKUP_FILE}" in
  /*) ;;
  *) BACKUP_FILE="$(pwd)/${BACKUP_FILE}" ;;
esac

cd "${INFRA_DIR}"

if [ ! -f "${BACKUP_FILE}" ]; then
  echo "Backup file not found: ${BACKUP_FILE}" >&2
  exit 1
fi

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

echo "This will WIPE the media_data volume (${ENV_NAME} stack - product images and"
echo "private expense receipts) and replace it with the contents of:"
echo "  ${BACKUP_FILE}"
echo "Everything uploaded after that backup was taken will be lost."
read -r -p "Type RESTORE MEDIA to confirm: " CONFIRM
if [ "${CONFIRM}" != "RESTORE MEDIA" ]; then
  echo "Confirmation did not match 'RESTORE MEDIA', aborting." >&2
  exit 1
fi

echo "Stopping backend and media-backup so nothing reads/writes media_data mid-restore..."
docker compose "${COMPOSE_ARGS[@]}" stop backend media-backup

echo "Wiping current media_data contents and extracting archive..."
# Reuses the "backend" service's own image/volumes (media_data:/data/media, see
# docker-compose(.prod).yml) rather than media-backup, which mounts media_data
# read-only by design - the extra -v below is the only place this whole toolchain
# mounts media_data read-write outside the running backend container itself.
docker compose "${COMPOSE_ARGS[@]}" run --rm --no-deps \
  -v "${BACKUP_FILE}:/tmp/restore.tar.gz:ro" \
  --entrypoint sh backend -c '
    set -eu
    find /data/media -mindepth 1 -delete
    tar xzf /tmp/restore.tar.gz -C /data/media
  '

echo "Restarting backend and media-backup..."
docker compose "${COMPOSE_ARGS[@]}" up -d backend media-backup

echo "Restore complete: media_data (${ENV_NAME} stack) now reflects ${BACKUP_FILE}."
