#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p out/tests
if command -v javac >/dev/null 2>&1; then
  javac --release 17 -encoding UTF-8 -d out/tests app/src/main/java/com/stemnote/core/*.java tests/CoreTest.java tests/AiCoreTest.java tests/SourceCheck.java
else
  # Some managed runtimes include the compiler module but omit javac and ct.sym.
  java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -Xlint:-options -encoding UTF-8 -d out/tests app/src/main/java/com/stemnote/core/*.java tests/CoreTest.java tests/AiCoreTest.java tests/SourceCheck.java
fi
java -ea -cp out/tests CoreTest
java -ea -cp out/tests AiCoreTest
java -ea -cp out/tests SourceCheck
