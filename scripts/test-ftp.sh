#!/bin/bash
set -e
ftp -n 172.28.0.11 <<FTPEOF
user alice password123
pwd
cd pub
ls
get README.txt /dev/null
cd /
get secret.doc /dev/null
quit
FTPEOF
