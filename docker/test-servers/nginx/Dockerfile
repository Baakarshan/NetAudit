ARG NGINX_IMAGE=docker.m.daocloud.io/library/nginx:alpine
FROM ${NGINX_IMAGE}

RUN mkdir -p /usr/share/nginx/html/admin /usr/share/nginx/html/api && \
    echo '<h1>Welcome to NetAudit Test</h1>' > /usr/share/nginx/html/index.html && \
    echo '<h1>Admin Panel (Sensitive!)</h1>' > /usr/share/nginx/html/admin/index.html && \
    echo '<h1>Secret Page</h1>' > /usr/share/nginx/html/admin/secret.html && \
    echo 'OK' > /usr/share/nginx/html/health && \
    echo '{"users": ["alice", "bob"]}' > /usr/share/nginx/html/api/users.json

COPY default.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
