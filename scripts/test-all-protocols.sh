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

echo -e "${GREEN}[1/6] Testing HTTP...${NC}"
curl -s -o /dev/null http://172.28.0.20/index.html
curl -s -o /dev/null http://172.28.0.20/admin/secret.html
curl -s -o /dev/null http://172.28.0.20/api/users.json
sleep 1

echo -e "${GREEN}[2/6] Testing FTP...${NC}"
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
nslookup example.com 172.28.0.13 || true
nslookup test.local 172.28.0.13 || true
nslookup malicious-very-long-domain-name-that-might-be-a-dns-tunnel-attempt.evil.com 172.28.0.13 || true
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
  } | nc 172.28.0.14 25 || true
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
