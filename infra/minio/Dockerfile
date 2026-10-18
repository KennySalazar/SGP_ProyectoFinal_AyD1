# The official registries no longer allow anonymous pulls of minio/minio.
# Keep the existing release using its official GitHub assets, not a third-party mirror.
# https://github.com/minio/minio/releases/tag/RELEASE.2025-07-23T15-54-02Z
FROM alpine:3.22 AS download

ARG TARGETARCH
RUN apk add --no-cache ca-certificates curl
RUN case "${TARGETARCH:-$(uname -m)}" in \
      amd64|x86_64) arch=amd64; checksum=eef6581f6509f43ece007a6f2eb4c5e3ce41498c8956e919a7ac7b4b170fa431 ;; \
      arm64|aarch64) arch=arm64; checksum=ec9f48fee97dee7e6ce8fe9f576471bcd52380c035091d8b075b64d606124c7e ;; \
      *) echo "Unsupported MinIO architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac && \
    curl --fail --location --retry 3 \
      "https://github.com/minio/minio/releases/download/RELEASE.2025-07-23T15-54-02Z/minio.linux-${arch}.RELEASE.2025-07-23T15-54-02Z" \
      --output /minio && \
    echo "$checksum  /minio" | sha256sum -c - && chmod 755 /minio
RUN curl --fail --location --retry 3 \
      https://raw.githubusercontent.com/minio/minio/RELEASE.2025-07-23T15-54-02Z/LICENSE \
      --output /LICENSE

FROM alpine:3.22
LABEL org.opencontainers.image.source="https://github.com/minio/minio" \
      org.opencontainers.image.version="RELEASE.2025-07-23T15-54-02Z" \
      org.opencontainers.image.licenses="AGPL-3.0-only"
COPY --from=download /minio /usr/bin/minio
COPY --from=download /LICENSE /licenses/LICENSE
COPY --from=download /etc/ssl/certs/ca-certificates.crt /etc/ssl/certs/
EXPOSE 9000 9001
VOLUME ["/data"]
ENTRYPOINT ["/usr/bin/minio"]
CMD ["server", "/data"]
