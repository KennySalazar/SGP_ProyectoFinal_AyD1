# syntax=docker/dockerfile:1.7

FROM --platform=$BUILDPLATFORM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm \
    npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine
ARG BUILD_DATE="unknown"
ARG VCS_REF="unknown"
ARG VERSION="development"

LABEL org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="https://github.com/KennySalazar/SGP_ProyectoFinal_AyD1" \
      org.opencontainers.image.title="SGP Client" \
      org.opencontainers.image.version="${VERSION}"

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/sgp-client/browser /usr/share/nginx/html
USER 101
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --spider http://127.0.0.1:8080/healthz || exit 1
