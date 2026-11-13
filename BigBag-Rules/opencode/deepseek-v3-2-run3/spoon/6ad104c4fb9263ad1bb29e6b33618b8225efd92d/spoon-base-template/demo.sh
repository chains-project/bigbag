#!/bin/bash
# Demonstration script for the generic package migration transformation
# Shows how to fix the BouncyCastle TLS breaking change in bcprov-jdk15on 1.67

set -e

echo "========================================="
echo "Generic Package Migration Demonstration"
echo "========================================="
echo
echo "This demonstrates fixing the BouncyCastle TLS breaking change:"
echo "Old: org.bouncycastle.crypto.tls.*"
echo "New: org.bouncycastle.tls.*"
echo

# Create a simple test file with the old imports
TEST_DIR="/tmp/bc-migration-test"
rm -rf "$TEST_DIR"
mkdir -p "$TEST_DIR/src/main/java/com/example"

cat > "$TEST_DIR/src/main/java/com/example/TestClass.java" << 'EOF'
package com.example;

import org.bouncycastle.crypto.tls.TlsFatalAlert;
import org.bouncycastle.crypto.tls.AlertDescription;

public class TestClass {
    public void checkMAC(byte[] received, byte[] calculated) {
        if (!java.util.Arrays.equals(received, calculated)) {
            throw new TlsFatalAlert(AlertDescription.bad_record_mac);
        }
    }
    
    public AlertDescription getDescription() {
        return AlertDescription.bad_record_mac;
    }
}
EOF

echo "Created test file at: $TEST_DIR/src/main/java/com/example/TestClass.java"
echo
echo "Original file contents:"
echo "-----------------------"
cat "$TEST_DIR/src/main/java/com/example/TestClass.java"
echo

# Build the transformation if needed
cd /workspace/spoon-base-template
mvn package -q

echo
echo "Running transformation..."
echo "-------------------------"

java -cp "target/spoon-base-1.0-SNAPSHOT.jar:$(cat classpath.txt)" \
  github.chains.Main "$TEST_DIR/src/main/java" \
  org.bouncycastle.crypto.tls \
  org.bouncycastle.tls 2>&1 | grep -v "SLF4J"

echo
echo "Transformed file contents:"
echo "--------------------------"
cat "$TEST_DIR/src/main/java-migrated/com/example/TestClass.java"

echo
echo "========================================="
echo "Transformation Complete!"
echo "========================================="
echo
echo "Summary of changes:"
echo "1. Updated imports from org.bouncycastle.crypto.tls to org.bouncycastle.tls"
echo "2. Updated type references in code"
echo "3. Output written to: $TEST_DIR/src/main/java-migrated"
echo
echo "Next steps for BouncyCastle migration:"
echo "1. Add bctls-jdk15on dependency to your project"
echo "2. Copy transformed files to your source directory"
echo "3. Compile and test"
echo
echo "Dependency to add to pom.xml:"
cat << 'EOF'
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bctls-jdk15on</artifactId>
  <version>1.67</version>
</dependency>
EOF

echo
echo "The transformation is generic and can be used for any package migration!"
echo "Example: java ... github.chains.Main ./src javax.xml.bind jakarta.xml.bind"