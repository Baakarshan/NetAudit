FROM alpine:3.19

RUN apk add --no-cache busybox-extras shadow && \
    adduser -D -s /bin/sh testuser && \
    echo 'testuser:password123' | chpasswd

CMD ["telnetd", "-F", "-l", "/bin/login"]

EXPOSE 23
