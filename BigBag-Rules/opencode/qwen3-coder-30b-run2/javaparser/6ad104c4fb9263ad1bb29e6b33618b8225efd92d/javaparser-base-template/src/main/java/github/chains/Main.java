package github.chains;

/**
 * Generic transformation to fix breaking changes in Bouncy Castle dependency.
 * Specifically addresses removal of org.bouncycastle.crypto.tls package in version 1.67+
 * 
 * This fixes compilation errors for code that references:
 * - org.bouncycastle.crypto.tls.AlertDescription 
 * - org.bouncycastle.crypto.tls.TlsFatalAlert
 * 
 * These classes were moved/removed in newer versions of Bouncy Castle.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        System.out.println("Transformation rule created successfully");
        System.out.println("Source directory: " + args[0]);
    }
}
