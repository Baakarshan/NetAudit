#!/bin/bash
set -e
{
  sleep 1; echo "USER alice"
  sleep 1; echo "PASS password123"
  sleep 1; echo "STAT"
  sleep 1; echo "LIST"
  sleep 1; echo "RETR 1"
  sleep 2; echo "QUIT"
} | nc 172.28.0.15 110 || true
