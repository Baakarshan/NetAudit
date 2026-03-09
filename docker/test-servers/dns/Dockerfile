FROM alpine:3.19

RUN apk add --no-cache dnsmasq

COPY dnsmasq.conf /etc/dnsmasq.conf

CMD ["dnsmasq", "--no-daemon"]

EXPOSE 53/udp 53/tcp
