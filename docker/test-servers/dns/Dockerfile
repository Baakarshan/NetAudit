ARG PYTHON_IMAGE=docker.m.daocloud.io/library/python:3.12-alpine
ARG PIP_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple
FROM ${PYTHON_IMAGE}
ARG PIP_INDEX_URL

RUN pip install -i ${PIP_INDEX_URL} dnslib

RUN cat > /dns_server.py <<'PYEOF'
import time
from dnslib import DNSRecord, RR, A, QTYPE
from dnslib.server import BaseResolver, DNSServer


RESPONSES = {
    "example.com": "93.184.216.34",
    "test.local": "172.28.0.10",
    "very-long-domain-name-that-might-be-a-dns-tunnel.example.com": "6.6.6.6",
}


class FixedResolver(BaseResolver):
    def resolve(self, request, handler):
        qname = str(request.q.qname).rstrip(".")
        reply = request.reply()
        ip = RESPONSES.get(qname)
        if ip:
            reply.add_answer(RR(qname, QTYPE.A, rdata=A(ip), ttl=60))
        else:
            reply.header.rcode = 3
        return reply


server = DNSServer(FixedResolver(), port=53, address="0.0.0.0")
server.start_thread()
print("DNS server started on 0.0.0.0:53")
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    pass
PYEOF

CMD ["python", "/dns_server.py"]

EXPOSE 53/udp 53/tcp
