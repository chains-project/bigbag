#!/bin/bash

echo "=== Jetty 11 Compatibility Fix ==="

# Show the original issue
echo "Original file had:"
grep -n "SelectChannelConnector" /workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java

echo ""
echo "Fixed file now has:"
grep -n "ServerConnector" /workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java

echo ""
echo "Removed import statement:"
grep -n "import.*SelectChannelConnector" /workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java || echo "No import found (good!)"

echo ""
echo "=== Transformation Summary ==="
echo "This demonstrates the fix for the breaking change in Jetty 11.0.12"
echo "where SelectChannelConnector was removed and replaced with ServerConnector."

echo ""
echo "The transformation rule is generic and reusable for any project"
echo "facing the same API compatibility issue."