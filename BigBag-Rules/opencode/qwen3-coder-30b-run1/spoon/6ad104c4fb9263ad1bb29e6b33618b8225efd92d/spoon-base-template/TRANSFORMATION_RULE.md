/**
 * Generic Transformation Rule for Bouncy Castle API Changes (1.67+)
 * 
 * Problem: In Bouncy Castle 1.67+, the org.bouncycastle.crypto.tls package was removed
 * This breaks projects that depend on TlsFatalAlert and AlertDescription classes
 * 
 * Solution: 
 * 1. Remove imports of org.bouncycastle.crypto.tls.*
 * 2. Replace TlsFatalAlert constructor usage with IOException
 * 3. Replace AlertDescription constants with appropriate values
 * 
 * This transformation rule can be applied to any project with the same issue
 */

// Generic approach for applying to any project:
// 1. Find files with imports like: import org.bouncycastle.crypto.tls.*;
// 2. Find usages of: new TlsFatalAlert(AlertDescription.bad_record_mac)
// 3. Replace with: throw new IOException("Bad record MAC")

// The actual implementation would use Spoon to:
// - Scan all Java files in the source directory
// - Find imports from org.bouncycastle.crypto.tls package
// - Find usages of TlsFatalAlert constructor
// - Find usages of AlertDescription constants
// - Replace them with appropriate alternatives

// For the specific HAP-Java case, the fix was:
// - Remove imports of org.bouncycastle.crypto.tls.*
// - Replace: throw new TlsFatalAlert(AlertDescription.bad_record_mac);
// - With: throw new IOException("Bad record MAC");

// This is a generic rule that can be applied to other projects with the same breaking change.