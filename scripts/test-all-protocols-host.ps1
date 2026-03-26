$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  NetAudit - Host Traffic Generator" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$httpHost = "127.0.0.1"
$httpPort = 19080
$ftpHost = "127.0.0.1"
$ftpPort = 2121
$telnetHost = "127.0.0.1"
$telnetPort = 2323
$dnsHost = "127.0.0.1"
$dnsPort = 1053
$smtpHost = "127.0.0.1"
$smtpPort = 2525
$pop3Host = "127.0.0.1"
$pop3Port = 2110

Write-Host "[1/6] HTTP" -ForegroundColor Green
Invoke-WebRequest -Uri "http://$httpHost`:$httpPort/index.html" -UseBasicParsing | Out-Null
Invoke-WebRequest -Uri "http://$httpHost`:$httpPort/admin/secret.html" -UseBasicParsing | Out-Null
Invoke-WebRequest -Uri "http://$httpHost`:$httpPort/api/users.json" -UseBasicParsing | Out-Null
Start-Sleep -Seconds 1

Write-Host "[2/6] FTP" -ForegroundColor Green
$ftpCommands = @(
  "open $ftpHost $ftpPort",
  "user alice password123",
  "pwd",
  "cd pub",
  "ls",
  "get README.txt",
  "cd /",
  "get secret.doc",
  "bye"
)
$ftpScript = $ftpCommands -join "`r`n"
$ftpScript | ftp -n | Out-Null
Start-Sleep -Seconds 1

Write-Host "[3/6] TELNET" -ForegroundColor Green
$telnetPayload = @(
  "testuser",
  "password123",
  "whoami",
  "exit"
) -join "`r`n"
$telnetBytes = [System.Text.Encoding]::ASCII.GetBytes($telnetPayload + "`r`n")
$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($telnetHost, $telnetPort)
$stream = $client.GetStream()
$stream.Write($telnetBytes, 0, $telnetBytes.Length)
$stream.Flush()
$stream.Close()
$client.Close()
Start-Sleep -Seconds 1

Write-Host "[4/6] DNS" -ForegroundColor Green
try { Resolve-DnsName "example.com" -Server "8.8.8.8" | Out-Null } catch {}
try { Resolve-DnsName "test.local" -Server "8.8.8.8" | Out-Null } catch {}
try { Resolve-DnsName "very-long-domain-name-that-might-be-a-dns-tunnel.example.com" -Server "8.8.8.8" | Out-Null } catch {}
Start-Sleep -Seconds 1

Write-Host "[5/6] SMTP" -ForegroundColor Green
$smtpLines = @(
  'EHLO testclient',
  'MAIL FROM:<alice@test.com>',
  'RCPT TO:<bob@test.com>',
  'DATA',
  'From: alice@test.com',
  'To: bob@test.com',
  'Subject: Quarterly Report',
  'Content-Type: multipart/mixed; boundary="----=_TestBoundary"',
  '',
  '------=_TestBoundary',
  'Content-Type: text/plain',
  '',
  'Please see attached.',
  '------=_TestBoundary',
  'Content-Type: application/pdf; name="report.pdf"',
  'Content-Disposition: attachment; filename="report.pdf"',
  'Content-Transfer-Encoding: base64',
  '',
  'JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwo=',
  '------=_TestBoundary--',
  '.',
  'QUIT'
)
$smtpPayload = ($smtpLines -join "`r`n") + "`r`n"
$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($smtpHost, $smtpPort)
$stream = $client.GetStream()
$bytes = [System.Text.Encoding]::ASCII.GetBytes($smtpPayload)
$stream.Write($bytes, 0, $bytes.Length)
$stream.Flush()
$stream.Close()
$client.Close()
Start-Sleep -Seconds 1

Write-Host "[6/6] POP3" -ForegroundColor Green
$pop3Lines = @(
  "USER alice",
  "PASS password123",
  "STAT",
  "LIST",
  "RETR 1",
  "QUIT"
)
$pop3Payload = ($pop3Lines -join "`r`n") + "`r`n"
$client = New-Object System.Net.Sockets.TcpClient
$client.Connect($pop3Host, $pop3Port)
$stream = $client.GetStream()
$bytes = [System.Text.Encoding]::ASCII.GetBytes($pop3Payload)
$stream.Write($bytes, 0, $bytes.Length)
$stream.Flush()
$stream.Close()
$client.Close()
Start-Sleep -Seconds 1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Host traffic generated. Check Dashboard" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
