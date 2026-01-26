#!/bin/bash

# Muusikoiden-bot UNRAID User Script
# Schedule: Set to run every 1 hour in User Scripts plugin

# Configuration
CONTAINER_NAME="muusikoiden-bot"
IMAGE_NAME="muusikoiden-bot"
DATA_PATH="/mnt/user/muusikoiden-bot"
SOURCE_PATH="/mnt/user/muusikoiden-bot"

# Environment variables (set these in the script or via UNRAID GUI)
export TELEGRAM_API_KEY="your-api-key-here"
export TELEGRAM_CHAT_ID="your-chat-id-here"
export TELEGRAM_ENABLED="true"

# Build image if it doesn't exist
if ! docker image inspect "$IMAGE_NAME" &>/dev/null; then
    echo "Building $IMAGE_NAME image..."
    docker build -t "$IMAGE_NAME" "$SOURCE_PATH"
fi

# Run the container
echo "Starting muusikoiden-bot..."
docker run --rm \
    --name "$CONTAINER_NAME" \
    -e TELEGRAM_API_KEY="$TELEGRAM_API_KEY" \
    -e TELEGRAM_CHAT_ID="$TELEGRAM_CHAT_ID" \
    -e TELEGRAM_ENABLED="$TELEGRAM_ENABLED" \
    -v "$DATA_PATH/data:/app/data" \
    "$IMAGE_NAME"

echo "Muusikoiden-bot execution completed."
