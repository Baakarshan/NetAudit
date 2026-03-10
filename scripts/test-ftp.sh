#!/bin/bash
set -e
FTP_HOST="172.28.0.11"
FTP_PORT="21"

for i in 1 2 3 4 5 6 7 8 9 10; do
  if nc -z -w 1 "$FTP_HOST" "$FTP_PORT" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
FTP_MODE=""
if command -v ftp >/dev/null 2>&1; then
  FTP_MODE="ftp"
elif command -v lftp >/dev/null 2>&1; then
  FTP_MODE="lftp"
elif command -v busybox >/dev/null 2>&1 && busybox --list | grep -x ftp >/dev/null 2>&1; then
  FTP_MODE="busybox"
else
  echo "未找到FTP客户端，请安装ftp或lftp或busybox-extras。" >&2
  exit 1
fi

if [ "$FTP_MODE" = "ftp" ]; then
  ftp -n "$FTP_HOST" <<FTPEOF
user alice password123
pwd
cd pub
ls
get README.txt /dev/null
cd /
get secret.doc /dev/null
quit
FTPEOF
elif [ "$FTP_MODE" = "lftp" ]; then
  lftp -u alice,password123 "$FTP_HOST" -e "set net:timeout 10; set net:max-retries 1; set ftp:ssl-allow no; set ftp:passive-mode off; set xfer:clobber yes; pwd; cd pub; ls; get README.txt -o /tmp/README.txt; cd /; get secret.doc -o /tmp/secret.doc; bye"
else
  busybox ftp -n "$FTP_HOST" <<FTPEOF
user alice password123
pwd
cd pub
ls
get README.txt /dev/null
cd /
get secret.doc /dev/null
quit
FTPEOF
fi
