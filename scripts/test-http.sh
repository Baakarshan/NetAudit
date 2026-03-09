#!/bin/bash
set -e
curl -s -o /dev/null http://172.28.0.20/index.html
curl -s -o /dev/null http://172.28.0.20/admin/secret.html
curl -s -o /dev/null http://172.28.0.20/api/users.json
