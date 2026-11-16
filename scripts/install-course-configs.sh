#!/bin/bash
set -e

SRC="/workspace/course/configs"
DST="/workspace/chipyard/generators/chipyard/src/main/scala/config"

if [ ! -d "$SRC" ]; then
    echo "Course configuration directory not found: $SRC"
    exit 1
fi

echo "Linking course Chipyard configurations..."

for file in "$SRC"/*.scala; do
    if [ -e "$file" ]; then
        name=$(basename "$file")

        ln -sf "$file" "$DST/$name"

        echo "  $name"
    fi
done

# Student-owned configs are linked too, so they remain stored under the
# repository's writable student-work mount while Chipyard can discover them.
STUDENT_SRC="/workspace/student-work"
if [ -d "$STUDENT_SRC" ]; then
    echo "Linking student Chipyard configurations..."
    while IFS= read -r -d '' file; do
        name=$(basename "$file")
        ln -sf "$file" "$DST/$name"
        echo "  $name"
    done < <(find "$STUDENT_SRC" -type f -name '*.scala' -print0)
fi

echo "Course configurations ready."
