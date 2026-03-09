#!/bin/sh
set -e

HOST_PORT=$1
shift
TIMEOUT=30

HOST=$(echo "$HOST_PORT" | cut -d: -f1)
PORT=$(echo "$HOST_PORT" | cut -d: -f2)

start_ts=$(date +%s)

echo "Waiting for $HOST:$PORT..."

while :; do
  if nc -z "$HOST" "$PORT" >/dev/null 2>&1; then
    echo "$HOST:$PORT is available"
    break
  fi
  now_ts=$(date +%s)
  if [ $((now_ts - start_ts)) -ge $TIMEOUT ]; then
    echo "Timeout waiting for $HOST:$PORT"
    exit 1
  fi
  sleep 1
done

exec "$@"
