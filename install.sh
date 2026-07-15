#!/bin/bash
set -e

# SecAI Installation Script for Linux & macOS
# Usage: curl -sSL https://raw.githubusercontent.com/<YOUR_GITHUB_USERNAME>/secai/main/install.sh | bash

REPO="REACH-ARC/SecAI"
BINARY_NAME="secai"
INSTALL_DIR="/usr/local/bin"

echo "detecting OS and Architecture..."
OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
ARCH="$(uname -m)"

if [ "$ARCH" = "x86_64" ]; then
    ARCH="amd64"
elif [ "$ARCH" = "aarch64" ] || [ "$ARCH" = "arm64" ]; then
    ARCH="arm64"
else
    echo "Unsupported architecture: $ARCH"
    exit 1
fi

if [ "$OS" != "linux" ] && [ "$OS" != "darwin" ]; then
    echo "Unsupported OS: $OS"
    exit 1
fi

# Fetch the latest release tag from GitHub
LATEST_TAG=$(curl -s "https://api.github.com/repos/$REPO/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')

if [ -z "$LATEST_TAG" ]; then
    echo "Error: Could not determine latest release for $REPO"
    exit 1
fi

DOWNLOAD_URL="https://github.com/$REPO/releases/download/$LATEST_TAG/secai-${OS}-${ARCH}"

echo "Downloading SecAI $LATEST_TAG for ${OS}-${ARCH}..."
curl -sSL -o "$BINARY_NAME" "$DOWNLOAD_URL"
chmod +x "$BINARY_NAME"

echo "Installing to $INSTALL_DIR..."
if [ -w "$INSTALL_DIR" ]; then
    mv "$BINARY_NAME" "$INSTALL_DIR/"
else
    echo "Requires sudo privileges to move binary to $INSTALL_DIR"
    sudo mv "$BINARY_NAME" "$INSTALL_DIR/"
fi

echo "SecAI successfully installed!"
echo "Run 'secai --help' to get started."
