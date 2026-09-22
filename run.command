#!/bin/bash
# Double-click this file in Finder to compile (if needed) and run the
# spell checker. It automatically finds its own folder, so it works
# no matter where you moved it.

cd "$(dirname "$0")"

echo "Compiling..."
javac -d out src/main/java/spellcheck/*.java

if [ $? -ne 0 ]; then
    echo ""
    echo "Compilation failed - see errors above."
    read -p "Press Enter to close this window..."
    exit 1
fi

echo "Running..."
echo ""
java -cp out spellcheck.SpellChecker

echo ""
read -p "Press Enter to close this window..."
