#!/bin/bash

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTENT_DIR="$SCRIPT_DIR/content"

# Check if content directory exists
if [ ! -d "$CONTENT_DIR" ]; then
    echo "Error: Content directory not found: $CONTENT_DIR"
    echo "Please export thread data content.zip and unzip it into the 'content' directory."
    exit 1
fi

# Check if content directory has any .txt files
TXT_FILE_COUNT=$(find "$CONTENT_DIR" -maxdepth 1 -name "*.txt" -type f | wc -l)
if [ "$TXT_FILE_COUNT" -eq 0 ]; then
    echo "Error: No .txt files found in: $CONTENT_DIR"
    echo "Please export thread data content.zip and unzip it into the 'content' directory."
    exit 1
fi

echo "Found $TXT_FILE_COUNT .txt file(s) in content directory"
echo "Proceeding with analysis..."
echo ""

npm run process
