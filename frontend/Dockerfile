FROM node:22.12-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV VITE_DEMO_MODE=false
RUN npm run build

FROM nginx:1.27-alpine
RUN apk add --no-cache openssl && mkdir -p /etc/nginx/certs
COPY deploy/nginx-tls.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
RUN printf '#!/bin/sh\nif [ ! -f /etc/nginx/certs/server.key ]; then\n  openssl req -x509 -nodes -days 30 -newkey rsa:2048 -keyout /etc/nginx/certs/server.key -out /etc/nginx/certs/server.crt -subj "/CN=localhost" >/dev/null 2>&1\nfi\n' > /docker-entrypoint.d/40-generate-selfsigned-cert.sh && \
    chmod +x /docker-entrypoint.d/40-generate-selfsigned-cert.sh
EXPOSE 80 443
HEALTHCHECK --interval=15s --timeout=3s CMD wget --no-check-certificate -qO- https://127.0.0.1/ || exit 1
