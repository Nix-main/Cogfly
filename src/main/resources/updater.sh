#!/bin/bash
A="$1"
PATH="$2"
B="$3"
echo "Waiting for Cogfly to close..."
while kill -0 "$A" 2>/dev/null; do
    sleep 0.5
done
echo "Updating..."
if ! "$B" "$PATH"; then
    echo "Update failed."
    exit 1
fi
echo "Relaunching..."
nohup "$PATH" >/dev/null 2>&1 &