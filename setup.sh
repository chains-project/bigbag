#!/bin/bash
set -e

ROSEAU_REPO="https://github.com/alien-tools/roseau.git"
ROSEAU_DIR="/tmp/roseau"

echo "=== Installing roseau-core 0.6.0-SNAPSHOT ==="

# Clone or update
if [ -d "$ROSEAU_DIR/.git" ]; then
    echo "Roseau already cloned, pulling latest..."
    git -C "$ROSEAU_DIR" pull --quiet
else
    echo "Cloning Roseau..."
    git clone --quiet "$ROSEAU_REPO" "$ROSEAU_DIR"
fi

# Install roseau-core into local ~/.m2
echo "Building and installing roseau-core..."
mvn -f "$ROSEAU_DIR/pom.xml" install \
    -pl core -am \
    -DskipTests \
    -Dmaven.javadoc.skip=true \
    --quiet

echo ""
echo "=== Done. You can now build the project: ==="
echo "    mvn clean package -DskipTests"
