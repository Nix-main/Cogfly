#!/bin/sh
A="$1"
V="$2"
B="$3"

echo "Waiting for Cogfly to close..."
while kill -0 "$A" 2>/dev/null; do
    sleep 0.5
done
echo "Updating..."
C=$("$B" "$V" 2>&1)
R=$?
echo "$C"

if [ $R -ne 0 ]; then
    echo "Update failed."
    exit 1
fi
N=$(printf '%s\n' "$C" | sed -n 's/^Update successful\. New file created: //p')

if [ -n "$N" ] && [ -f "$N" ]; then
    L="$N"
    if [ "$N" != "$V" ] && [ -f "$V" ]; then
        rm -f "$V"
    fi
else
    L="$V"
fi
echo "Relaunching..."
nohup "$L" >/dev/null 2>&1 &