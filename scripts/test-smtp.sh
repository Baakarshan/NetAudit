#!/bin/bash
set -e
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
