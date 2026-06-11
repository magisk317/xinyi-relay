# Stage 1: Build webui
FROM --platform=$BUILDPLATFORM node:24-alpine AS webui-build
RUN corepack enable pnpm
WORKDIR /src
COPY frontend/shared ./frontend/shared
COPY frontend/webui ./frontend/webui
WORKDIR /src/frontend/webui
RUN CI=true pnpm install
RUN pnpm run build

# Stage 2: Build backend
FROM --platform=$BUILDPLATFORM golang:1.26.4-alpine3.23 AS build
WORKDIR /src
ARG GOPROXY=https://goproxy.cn,direct
ARG TARGETOS
ARG TARGETARCH
ENV GOPROXY=${GOPROXY}
COPY backend/api/go.mod backend/api/go.sum ./
RUN go mod download
COPY backend/api ./
COPY --from=webui-build /src/frontend/webui/dist ./webui_dist
RUN CGO_ENABLED=0 GOOS=${TARGETOS:-linux} GOARCH=${TARGETARCH:-amd64} go build -o /out/relayd ./cmd/relayd

# Stage 3: Final image
FROM alpine:3.24.0
WORKDIR /app
COPY --from=build /out/relayd /usr/local/bin/relayd
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/relayd"]
