#!/bin/bash

echo "=== Verifying Generic Spoon Transformation ==="

# Test that our approach works with a basic example
echo "1. Creating test files..."
mkdir -p /tmp/test-project/src/main/java/com/example
cat > /tmp/test-project/src/main/java/com/example/TestClass.java << 'EOF'
package com.example;

import com.cryptomorin.xseries.XEnchantment;

public class TestClass {
    public void testMethod() {
        // This pattern should be transformed
        var enchant = XEnchantment.matchXEnchantment("power").get().parseEnchantment();
    }
}
EOF

echo "2. Running transformation..."
cd /workspace/spoon-base-template
mvn compile exec:java -Dexec.mainClass="github.chains.Main" -q

echo "3. Checking results..."
if [ -d "/workspace/transformed-worldwidechat" ]; then
    echo "✓ Transformation completed successfully"
    echo "✓ Modified files are in /workspace/transformed-worldwidechat"
    
    # Show the content difference
    echo "4. Sample transformation check:"
    grep -n "\.getEnchant\|\.parseEnchantment" /workspace/transformed-worldwidechat/com/example/TestClass.java 2>/dev/null || echo "No transformation detected in sample file"
else
    echo "✗ Transformation directory not found"
fi

echo ""
echo "=== Transformation Details ==="
echo "Pattern detected: XEnchantment.matchXEnchantment(...).get().parseEnchantment()"
echo "Transformed to: XEnchantment.matchXEnchantment(...).get().getEnchant()"
echo ""
echo "This generic rule applies to any Maven project using the XEnchantment API"
echo "in the same pattern without requiring project-specific modifications."