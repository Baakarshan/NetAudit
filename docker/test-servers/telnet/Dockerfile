ARG ALPINE_IMAGE=alpine:3.19
FROM ${ALPINE_IMAGE}

RUN apk add --no-cache busybox-extras shadow && \
    adduser -D -s /bin/sh testuser && \
    echo 'testuser:password123' | chpasswd

CMD ["telnetd", "-F", "-l", "/bin/login"]

EXPOSE 23
