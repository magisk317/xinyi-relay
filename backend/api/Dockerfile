# Stage 1: Build webui
FROM --platform=$BUILDPLATFORM node:24-bookworm-slim AS webui-build
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG ALL_PROXY
ARG NO_PROXY
ARG http_proxy
ARG https_proxy
ARG all_proxy
ARG no_proxy
ARG PNPM_CONFIG_REGISTRY=https://registry.npmjs.org
ENV HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    ALL_PROXY=${ALL_PROXY} \
    NO_PROXY="${NO_PROXY},registry.npmjs.org,registry.npmmirror.com,auth.docker.io,registry-1.docker.io" \
    http_proxy=${http_proxy} \
    https_proxy=${https_proxy} \
    all_proxy=${all_proxy} \
    no_proxy="${no_proxy},registry.npmjs.org,registry.npmmirror.com,auth.docker.io,registry-1.docker.io"
WORKDIR /src
COPY frontend/shared ./frontend/shared
COPY frontend/webui ./frontend/webui
WORKDIR /src/frontend/webui
RUN corepack enable && corepack prepare "$(node -p "require('./package.json').packageManager")" --activate
RUN pnpm config set registry "${PNPM_CONFIG_REGISTRY}"
RUN CI=true pnpm install --frozen-lockfile || \
    (echo "Configured npm registry failed, falling back to official registry" >&2 && \
     pnpm config set registry https://registry.npmjs.org && \
     CI=true pnpm install --frozen-lockfile)
RUN pnpm run build

# Stage 2: Build backend
FROM --platform=$BUILDPLATFORM golang:1.27.0-alpine3.23 AS build
WORKDIR /src
ARG GOPROXY=https://proxy.golang.org,direct
ARG HTTP_PROXY
ARG HTTPS_PROXY
ARG ALL_PROXY
ARG NO_PROXY
ARG http_proxy
ARG https_proxy
ARG all_proxy
ARG no_proxy
ARG TARGETOS
ARG TARGETARCH
ENV GOPROXY=${GOPROXY} \
    HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    ALL_PROXY=${ALL_PROXY} \
    NO_PROXY=${NO_PROXY} \
    http_proxy=${http_proxy} \
    https_proxy=${https_proxy} \
    all_proxy=${all_proxy} \
    no_proxy=${no_proxy}
COPY backend/api/go.mod backend/api/go.sum ./
RUN go mod download
COPY backend/api ./
COPY --from=webui-build /src/frontend/webui/dist ./webui_dist
RUN CGO_ENABLED=0 GOOS=${TARGETOS:-linux} GOARCH=${TARGETARCH:-amd64} go build -o /out/relayd ./cmd/relayd

# Stage 3: Final image
FROM alpine:3.24.1
WORKDIR /app
COPY --from=build /out/relayd /usr/local/bin/relayd
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/relayd"]
