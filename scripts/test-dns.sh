#!/bin/bash
set -e
nslookup -timeout=2 -retry=1 example.com 172.28.0.13 || true
nslookup -timeout=2 -retry=1 test.local 172.28.0.13 || true
nslookup -timeout=2 -retry=1 very-long-domain-name-that-might-be-a-dns-tunnel.example.com 172.28.0.13 || true
