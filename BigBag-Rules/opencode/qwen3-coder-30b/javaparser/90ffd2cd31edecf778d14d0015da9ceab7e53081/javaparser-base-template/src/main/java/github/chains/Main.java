package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        // Generic transformation for fixing SLF4J API breakage in logback 1.4.0
        // Specifically addresses the removal of org.slf4j.spi.LoggingEventAware
        
        String sourceDirectory = args.length > 0 ? args[0] : "/workspace/pay-adminusers/src";
        String testDirectory = args.length > 1 ? args[1] : "/workspace/pay-adminusers/src/test";
        
        // Process main source files
        if (Files.exists(Paths.get(sourceDirectory))) {
            processDirectory(sourceDirectory);
        }
        
        // Process test source files
        if (Files.exists(Paths.get(testDirectory))) {
            processDirectory(testDirectory);
        }
    }
    
    private static void processDirectory(String directoryPath) throws IOException {
        SourceRoot sourceRoot = new SourceRoot(Paths.get(directoryPath));
        
        sourceRoot.parse("", (localPath, absolutePath, cu) -> {
            // Apply transformation to remove LoggingEventAware references
            cu.accept(new SLF4JLoggingEventAwareFixVisitor(), null);
            return SourceRoot.Callback.Result.SAVE;
        });
        
        sourceRoot.saveAll();
    }
    
    /**
     * Visitor to fix SLF4J LoggingEventAware related issues
     */
    private static class SLF4JLoggingEventAwareFixVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Remove any references to LoggingEventAware interface from implements clause
            n.getImplementedTypes().removeIf(type -> {
                String typeName = type.getNameAsString();
                return "LoggingEventAware".equals(typeName) || 
                       "org.slf4j.spi.LoggingEventAware".equals(type.getNameAsString());
            });
            return super.visit(n, arg);
        }
    }
}
