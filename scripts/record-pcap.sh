#!/bin/bash
set -e

echo "Starting tcpdump..."
tcpdump -i eth0 -w /test-data/sample.pcap -c 1000 &
PID=$!

echo "Generating traffic..."
bash /scripts/test-all-protocols.sh

echo "Waiting for capture to finish..."
sleep 5
kill $PID 2>/dev/null || true

echo "Recorded to /test-data/sample.pcap"
ls -la /test-data/sample.pcap
