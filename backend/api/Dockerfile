FROM --platform=$BUILDPLATFORM golang:1.26.3-alpine3.23 AS build
WORKDIR /src
ARG GOPROXY=https://goproxy.cn,direct
ARG TARGETOS
ARG TARGETARCH
ENV GOPROXY=${GOPROXY}
COPY go.mod go.sum ./
RUN go mod download
COPY . ./
RUN CGO_ENABLED=0 GOOS=${TARGETOS:-linux} GOARCH=${TARGETARCH:-amd64} go build -o /out/relayd ./cmd/relayd

FROM alpine:3.23.4
WORKDIR /app
COPY --from=build /out/relayd /usr/local/bin/relayd
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/relayd"]
