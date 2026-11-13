package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Generic JavaParser transformation for fixing Bouncy Castle API breakages
 * specifically targeting the removal of org.bouncycastle.crypto.tls package in 1.67
 */
public class Main {
    
    public static void main(String[] args) {
        try {
            Path sourceDir = Paths.get("/workspace/HAP-Java/src/main/java");
            
            // Process all Java files in the source directory
            Files.walk(sourceDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::processFile);
                
            System.out.println("Transformation completed successfully!");
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void processFile(Path filePath) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(new FileInputStream(filePath.toFile()));
            
            // Apply transformation to fix TLS API breakage
            cu.accept(new BCtlsFixVisitor(), null);
            
            // Save back to file
            try (FileOutputStream fos = new FileOutputStream(filePath.toFile())) {
                fos.write(cu.toString().getBytes());
            }
        } catch (Exception e) {
            System.err.println("Error processing " + filePath + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Visitor that fixes Bouncy Castle TLS API usage
     */
    private static class BCtlsFixVisitor extends ModifierVisitor<Void> {
        @Override
        public com.github.javaparser.ast.Node visit(Name name, Void arg) {
            String nameStr = name.toString();
            
            // Replace old TLS package references
            if (nameStr.equals("org.bouncycastle.crypto.tls.AlertDescription")) {
                System.out.println("Replacing AlertDescription with org.bouncycastle.tls.AlertDescription");
                return new Name("org.bouncycastle.tls.AlertDescription");
            }
            
            if (nameStr.equals("org.bouncycastle.crypto.tls.TlsFatalAlert")) {
                System.out.println("Replacing TlsFatalAlert with org.bouncycastle.tls.TlsFatalAlert");
                return new Name("org.bouncycastle.tls.TlsFatalAlert");
            }
            
            return super.visit(name, arg);
        }
    }
}