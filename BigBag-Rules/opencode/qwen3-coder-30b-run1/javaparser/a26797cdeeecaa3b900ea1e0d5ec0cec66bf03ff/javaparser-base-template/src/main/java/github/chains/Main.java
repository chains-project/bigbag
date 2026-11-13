package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transform.jar <source-directory>");
            System.exit(1);
        }
        
        File sourceDir = new File(args[0]);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Source directory does not exist: " + sourceDir.getAbsolutePath());
            System.exit(1);
        }
        
        // Find all Java files in the source directory
        File[] javaFiles = sourceDir.listFiles((dir, name) -> name.endsWith(".java"));
        if (javaFiles == null) {
            System.err.println("No Java files found in directory: " + sourceDir.getAbsolutePath());
            System.exit(1);
        }
        
        int filesModified = 0;
        
        for (File javaFile : javaFiles) {
            try {
                CompilationUnit cu = StaticJavaParser.parse(javaFile);
                
                // Visit all class declarations
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
                        // Check if the class implements LoggingEventAware
                        List<ClassOrInterfaceType> implementedTypes = classDecl.getImplementedTypes();
                        for (int i = 0; i < implementedTypes.size(); i++) {
                            ClassOrInterfaceType type = implementedTypes.get(i);
                            if (type.getNameAsString().equals("org.slf4j.spi.LoggingEventAware")) {
                                System.out.println("Removing LoggingEventAware from " + classDecl.getNameAsString() + " in " + javaFile.getName());
                                classDecl.getImplementedTypes().remove(i);
                                filesModified++;
                                // Break to avoid index shifting issues
                                return;
                            }
                        }
                        super.visit(classDecl, arg);
                    }
                }, null);
                
                // Write the modified file back
                com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter.print(cu);
            } catch (Exception e) {
                System.err.println("Error processing file " + javaFile.getAbsolutePath() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Transformation completed. Files modified: " + filesModified);
    }
}