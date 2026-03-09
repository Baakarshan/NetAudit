#!/bin/bash
set -e
{
  sleep 2
  echo "testuser"
  sleep 1
  echo "password123"
  sleep 1
  echo "whoami"
  sleep 1
  echo "ls -la"
  sleep 1
  echo "exit"
} | telnet 172.28.0.12 2>/dev/null || true
