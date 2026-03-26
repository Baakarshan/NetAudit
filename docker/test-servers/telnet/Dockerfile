ARG ALPINE_IMAGE=docker.m.daocloud.io/library/alpine:3.19
ARG ALPINE_MIRROR=https://mirrors.tuna.tsinghua.edu.cn/alpine
FROM ${ALPINE_IMAGE}
ARG ALPINE_MIRROR

RUN sed -i "s|https://dl-cdn.alpinelinux.org/alpine|${ALPINE_MIRROR}|g" /etc/apk/repositories && \
    apk add --no-cache busybox-extras shadow && \
    adduser -D -s /bin/sh testuser && \
    echo 'testuser:password123' | chpasswd

CMD ["telnetd", "-F", "-l", "/bin/login"]

EXPOSE 23
