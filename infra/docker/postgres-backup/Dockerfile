# Sidecar that periodically dumps the "postgres" service to a gzip file on a host
# bind-mount (see ../../docker-compose.yml / docker-compose.prod.yml) and prunes old
# dumps. Built on postgres:16-alpine (same major version as the "postgres" service)
# purely because it already ships pg_dump - it never runs postgres itself.
FROM postgres:16-alpine

COPY backup.sh /usr/local/bin/backup.sh
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/entrypoint.sh

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
