# syntax=docker/dockerfile:1.7
# WAVE web app (Flutter web), served by unprivileged nginx. The API is reached on the same origin
# through the load balancer, so no API URL is baked in.
ARG FLUTTER_VERSION=3.47.6
FROM ghcr.io/cirruslabs/flutter:${FLUTTER_VERSION} AS build
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
