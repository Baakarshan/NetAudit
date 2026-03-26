ARG PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
FROM ${PYTHON_IMAGE}

RUN cat > /pop3_server.py <<'PYEOF'
import socketserver

TEST_MAIL = (
    "From: sender@test.com\r\n"
    "To: alice@test.com\r\n"
    "Subject: Test Email with Attachment\r\n"
    "Content-Type: multipart/mixed; boundary=\"----=_Part_001\"\r\n"
    "\r\n"
    "------=_Part_001\r\n"
    "Content-Type: text/plain\r\n"
    "\r\n"
    "Hello, this is a test email.\r\n"
    "------=_Part_001\r\n"
    "Content-Type: application/pdf; name=\"report.pdf\"\r\n"
    "Content-Disposition: attachment; filename=\"report.pdf\"\r\n"
    "Content-Transfer-Encoding: base64\r\n"
    "\r\n"
    "JVBERi0xLjQKMSAwIG9iago8PAovVHlwZSAvQ2F0YWxvZwovUGFnZXMgMiAwIFIKPj4KZW5k\r\n"
    "b2JqCjIgMCBvYmoKPDwKL1R5cGUgL1BhZ2VzCi9LaWRzIFszIDAgUl0KL0NvdW50IDEKL01l\r\n"
    "------=_Part_001--\r\n"
)

class POP3Handler(socketserver.StreamRequestHandler):
    def handle(self):
        self.wfile.write(b"+OK POP3 server ready\r\n")
        self.wfile.flush()
        while True:
            try:
                line = self.rfile.readline().decode().strip()
                if not line:
                    break
                parts = line.split(" ", 1)
                cmd = parts[0].upper()
                if cmd == "USER":
                    self.wfile.write(b"+OK\r\n")
                elif cmd == "PASS":
                    self.wfile.write(b"+OK Logged in\r\n")
                elif cmd == "STAT":
                    self.wfile.write(b"+OK 1 " + str(len(TEST_MAIL)).encode() + b"\r\n")
                elif cmd == "LIST":
                    self.wfile.write(b"+OK 1 messages\r\n")
                    self.wfile.write(b"1 " + str(len(TEST_MAIL)).encode() + b"\r\n")
                    self.wfile.write(b".\r\n")
                elif cmd == "RETR":
                    self.wfile.write(b"+OK\r\n")
                    self.wfile.write(TEST_MAIL.encode())
                    self.wfile.write(b"\r\n.\r\n")
                elif cmd == "QUIT":
                    self.wfile.write(b"+OK Bye\r\n")
                    break
                else:
                    self.wfile.write(b"-ERR Unknown command\r\n")
                self.wfile.flush()
            except Exception:
                break

class ThreadedTCPServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    allow_reuse_address = True

server = ThreadedTCPServer(("0.0.0.0", 110), POP3Handler)
print("POP3 server started on port 110")
server.serve_forever()
PYEOF

CMD ["python", "/pop3_server.py"]

EXPOSE 110
