#!/bin/bash
pid=$1
url=$2
sha=$3

echo "Waiting for Cogfly..."
while kill -0 "$pid" 2>/dev/null; do sleep 1; done

echo "Downloading..."
dmg=$(mktemp).dmg
curl -L "$url" -o "$dmg"

echo "Checking hash..."
[ "$(shasum -a 256 "$dmg" | cut -d' ' -f1)" = "$sha" ] || exit 1

echo "Installing..."
mnt=$(mktemp -d)
hdiutil attach "$dmg" -mountpoint "$mnt" -nobrowse -quiet
rm -rf /Applications/Cogfly.app
cp -R "$mnt/Cogfly.app" /Applications/
hdiutil detach "$mnt"

rm "$dmg"
open "cogfly://launch"