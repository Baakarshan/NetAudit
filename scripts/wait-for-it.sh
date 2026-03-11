#!/bin/sh
set -e

HOST_PORT=""
TIMEOUT=30

while [ $# -gt 0 ]; do
  case "$1" in
    --timeout=*)
      TIMEOUT="${1#*=}"
      shift
      ;;
    --timeout)
      TIMEOUT="$2"
      shift 2
      ;;
    --)
      shift
      break
      ;;
    *)
      if [ -z "$HOST_PORT" ]; then
        HOST_PORT="$1"
        shift
      else
        break
      fi
      ;;
  esac
done

if [ -z "$HOST_PORT" ]; then
  echo "Usage: wait-for-it host:port [--timeout=N] -- command" >&2
  exit 1
fi

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
