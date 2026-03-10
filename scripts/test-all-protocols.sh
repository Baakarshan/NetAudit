#!/bin/bash
# NetAudit 全协议测试脚本
set -e

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  NetAudit — Full Protocol Test Suite   ${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

echo -e "${YELLOW}[0/6] Waiting for services...${NC}"
sleep 3

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

echo -e "${GREEN}[1/6] Testing HTTP...${NC}"
curl -s -o /dev/null http://172.28.0.20/index.html
curl -s -o /dev/null http://172.28.0.20/admin/secret.html
curl -s -o /dev/null http://172.28.0.20/api/users.json
sleep 1

echo -e "${GREEN}[2/6] Testing FTP...${NC}"
FTP_HOST="172.28.0.11"
FTP_PORT="21"
for i in 1 2 3 4 5 6 7 8 9 10; do
  if nc -z -w 1 "$FTP_HOST" "$FTP_PORT" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

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
sleep 1

echo -e "${GREEN}[3/6] Testing TELNET...${NC}"
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
sleep 1

echo -e "${GREEN}[4/6] Testing DNS...${NC}"
nslookup -timeout=2 -retry=1 example.com 172.28.0.13 || true
nslookup -timeout=2 -retry=1 test.local 172.28.0.13 || true
nslookup -timeout=2 -retry=1 very-long-domain-name-that-might-be-a-dns-tunnel.example.com 172.28.0.13 || true
sleep 1

echo -e "${GREEN}[5/6] Testing SMTP...${NC}"
if command -v swaks &> /dev/null; then
  swaks \
    --to bob@test.com \
    --from alice@test.com \
    --server 172.28.0.14 \
    --port 25 \
    --header "Subject: Quarterly Report" \
    --body "Please find the attached report." \
    --attach /test-data/attachment.pdf \
    2>/dev/null || true
else
  {
    sleep 1; echo "EHLO testclient"
    sleep 1; echo "MAIL FROM:<alice@test.com>"
    sleep 1; echo "RCPT TO:<bob@test.com>"
    sleep 1; echo "DATA"
    sleep 1
    echo "From: alice@test.com"
    echo "To: bob@test.com"
    echo "Subject: Quarterly Report"
    echo "Content-Type: multipart/mixed; boundary=\"----=_TestBoundary\""
    echo ""
    echo "------=_TestBoundary"
    echo "Content-Type: text/plain"
    echo ""
    echo "Please see attached."
    echo "------=_TestBoundary"
    echo "Content-Type: application/pdf; name=\"report.pdf\""
    echo "Content-Disposition: attachment; filename=\"report.pdf\""
    echo "Content-Transfer-Encoding: base64"
    echo ""
    echo "JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwo="
    echo "------=_TestBoundary--"
    echo "."
    sleep 1; echo "QUIT"
  } | nc -w 5 172.28.0.14 25 || true
fi
sleep 1

echo -e "${GREEN}[6/6] Testing POP3...${NC}"
{
  sleep 1; echo "USER alice"
  sleep 1; echo "PASS password123"
  sleep 1; echo "STAT"
  sleep 1; echo "LIST"
  sleep 1; echo "RETR 1"
  sleep 2; echo "QUIT"
} | nc 172.28.0.15 110 || true
sleep 1

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}  All 6 protocols tested!               ${NC}"
echo -e "${CYAN}  Open Dashboard: http://localhost:5173  ${NC}"
echo -e "${CYAN}========================================${NC}"
