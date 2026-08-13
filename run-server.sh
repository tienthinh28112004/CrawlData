#!/bin/sh

JAR_PATH="target/crawlurlphim-1.0-SNAPSHOT-jar-with-dependencies.jar"

if [ ! -f "$JAR_PATH" ]; then
  echo "Jar not found: $JAR_PATH"
  exit 1
fi

while true; do
  java -Xms125m -Xmx512m -jar "$JAR_PATH" --server
  echo "Server stopped. Restarting in 5 seconds..."
  sleep 5
done
