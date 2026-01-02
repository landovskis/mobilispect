# syntax=docker/dockerfile:1.5

FROM mcr.microsoft.com/devcontainers/java:21-bookworm

ARG NODE_VERSION=24
ARG DEBIAN_FRONTEND=noninteractive

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates gnupg build-essential python3 python3-pip && \
    curl -fsSL https://deb.nodesource.com/setup_${NODE_VERSION}.x | bash - && \
    apt-get install -y nodejs && \
    npm install --global corepack && \
    corepack enable && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Pre-enable pnpm (Angular workspace may prefer it)
RUN corepack prepare pnpm@9.12.3 --activate

# Install Angular CLI for developer experience
RUN npm install --global @angular/cli@19

# Ensure gradle wrapper caches respect central location
ENV GRADLE_USER_HOME=/workspaces/.gradle-user-home

# Default workdir configured by devcontainer.json
WORKDIR /workspaces
