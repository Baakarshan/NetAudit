#!/bin/bash
set -e
nslookup example.com 172.28.0.13 || true
nslookup test.local 172.28.0.13 || true
nslookup malicious-very-long-domain-name-that-might-be-a-dns-tunnel-attempt.evil.com 172.28.0.13 || true
