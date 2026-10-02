# syntax=docker/dockerfile:1.7
# WAVE web app (Flutter web), served by unprivileged nginx. The API is reached on the same origin
# through the load balancer, so no API URL is baked in.
#
# Flutter is installed from the official repository at the exact version the app is tested with
# (community images lag behind releases).
FROM debian:bookworm-slim AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl git unzip xz-utils \
 && rm -rf /var/lib/apt/lists/*
ARG FLUTTER_VERSION=3.47.6
RUN git clone --depth 1 --branch ${FLUTTER_VERSION} https://github.com/flutter/flutter.git /opt/flutter
ENV PATH="/opt/flutter/bin:${PATH}"
RUN flutter config --no-analytics --no-cli-animations && flutter precache --web
WORKDIR /app
COPY pubspec.yaml pubspec.lock ./
RUN flutter pub get
COPY . .
RUN flutter build web --release --no-web-resources-cdn

FROM nginxinc/nginx-unprivileged:1.29-alpine
ENV WAVE_CONNECT_SRC="" \
    WAVE_TILE_HOSTS="https://tile.openstreetmap.org https://tiles.openseamap.org"
COPY nginx/templates /etc/nginx/templates
COPY --from=build /app/build/web /usr/share/nginx/html
EXPOSE 8080
