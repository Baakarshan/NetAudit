ARG PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
FROM ${PYTHON_IMAGE}

RUN pip install aiosmtpd

RUN cat > /smtp_server.py <<'PYEOF'
import asyncio
from aiosmtpd.controller import Controller
from aiosmtpd.handlers import Sink

class AuditSink(Sink):
    async def handle_DATA(self, server, session, envelope):
        print(f"Mail from: {envelope.mail_from}")
        print(f"Rcpt to: {envelope.rcpt_tos}")
        print(f"Data length: {len(envelope.content)}")
        return '250 OK'

controller = Controller(AuditSink(), hostname='0.0.0.0', port=25)
controller.start()
print("SMTP server started on port 25")
import signal
signal.pause()
PYEOF

CMD ["python", "/smtp_server.py"]

EXPOSE 25
