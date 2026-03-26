ARG FTP_BASE_IMAGE=docker.m.daocloud.io/library/alpine:3.19
FROM ${FTP_BASE_IMAGE}

RUN apk add --no-cache vsftpd shadow

RUN adduser -D -h /home/alice -s /bin/sh alice && \
    echo 'alice:password123' | chpasswd && \
    mkdir -p /home/alice/pub && \
    echo 'This is a secret document' > /home/alice/secret.doc && \
    echo 'Public readme' > /home/alice/pub/README.txt && \
    chown -R alice:alice /home/alice

RUN cat > /etc/vsftpd/vsftpd.conf <<'EOF'
listen=YES
listen_ipv6=NO
anonymous_enable=NO
local_enable=YES
write_enable=NO
local_umask=022
dirmessage_enable=YES
use_localtime=YES
xferlog_enable=YES
connect_from_port_20=YES
chroot_local_user=YES
allow_writeable_chroot=YES
pam_service_name=vsftpd
pasv_enable=YES
pasv_min_port=21100
pasv_max_port=21110
seccomp_sandbox=NO
ftpd_banner=Welcome to NetAudit FTP test server.
EOF

EXPOSE 21 21100-21110

CMD ["vsftpd", "/etc/vsftpd/vsftpd.conf"]
