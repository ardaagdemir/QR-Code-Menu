# Sidecar that periodically archives the media_data volume (product images + private
# expense receipts) to a host bind-mount and prunes old archives - same pattern as
# ../postgres-backup. Kept as its own minimal image with a read-only volume mount (see
# ../../docker-compose.yml / docker-compose.prod.yml) so this container can never write
# to media_data - restore is a separate, deliberate operation (../../scripts/restore-media.sh).
FROM alpine:3.20

COPY backup.sh /usr/local/bin/backup.sh
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
