ARG VSFTPD_IMAGE=docker.m.daocloud.io/fauria/vsftpd:latest
FROM ${VSFTPD_IMAGE}

RUN mkdir -p /home/vsftpd/alice/pub && \
    echo 'This is a secret document' > /home/vsftpd/alice/secret.doc && \
    echo 'Public readme' > /home/vsftpd/alice/pub/README.txt

ENV PASV_ENABLE=YES
ENV PASV_MIN_PORT=21100
ENV PASV_MAX_PORT=21110

EXPOSE 21 21100-21110
