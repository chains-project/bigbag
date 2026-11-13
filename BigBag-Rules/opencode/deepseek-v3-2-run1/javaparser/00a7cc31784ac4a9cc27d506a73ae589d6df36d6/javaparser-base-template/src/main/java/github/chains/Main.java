package github.chains;

/**
 * Generic JavaParser transformation for fixing breaking changes in dependency APIs.
 * 
 * This tool transforms Java source code to adapt to breaking changes in constructor
 * signatures when a dependency library removes parameters from constructors.
 * 
 * Usage: java -jar javaparser.jar <source-dir> <fully-qualified-class> <old-param-count> <new-param-count> [subclass1 subclass2 ...]
 * Example: java -jar javaparser.jar ./src org.apache.mina.filter.ssl.SslFilter 2 1 SSLFilter
 * 
 * This will:
 * 1. Find all constructor calls to the target class or specified subclasses
 * 2. Remove trailing parameters to match the new signature
 * 3. Find constructor declarations in subclasses that call super() with the old signature
 * 4. Update those constructors to match the new signature
 * 
 * Limitations:
 * - Only removes trailing parameters (doesn't handle parameter reordering or type changes)
 * - Requires manual specification of subclass names
 * - Uses simple name matching (not full type resolution)
 */
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory> <fully-qualified-class> <old-param-count> <new-param-count> [subclass1 subclass2 ...]");
            System.err.println("Example: java -jar javaparser.jar ./src org.apache.mina.filter.ssl.SslFilter 2 1 SSLFilter");
            System.err.println("This will remove the last parameter from 2-parameter constructors of SslFilter and its subclass SSLFilter");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String targetClassName = args[1];
        int oldParamCount = Integer.parseInt(args[2]);
        int newParamCount = Integer.parseInt(args[3]);
        
        if (newParamCount >= oldParamCount) {
            System.err.println("Error: new parameter count must be less than old parameter count (removing parameters)");
            System.exit(1);
        }
        
        // Collect subclass names (optional)
        java.util.Set<String> subclassNames = new java.util.HashSet<>();
        for (int i = 4; i < args.length; i++) {
            subclassNames.add(args[i]);
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Target class: " + targetClassName);
        System.out.println("Subclasses to check: " + subclassNames);
        System.out.println("Transforming constructors from " + oldParamCount + " to " + newParamCount + " parameters");
        
        try {
            List<File> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .map(Path::toFile)
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int totalChanges = 0;
            for (File javaFile : javaFiles) {
                int changes = processFile(javaFile, targetClassName, oldParamCount, newParamCount, subclassNames);
                totalChanges += changes;
                if (changes > 0) {
                    System.out.println("Modified " + javaFile.getPath() + " (" + changes + " changes)");
                }
            }
            
            System.out.println("Total changes made: " + totalChanges);
            
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static int processFile(File javaFile, String targetClassName, int oldParamCount, int newParamCount, java.util.Set<String> subclassNames) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        // Create visitor to fix constructor calls
        ConstructorFixVisitor visitor = new ConstructorFixVisitor(targetClassName, oldParamCount, newParamCount, subclassNames);
        cu.accept(visitor, null);
        
        int changes = visitor.getChangeCount();
        if (changes > 0) {
            // Write back the modified file
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String modifiedContent = printer.print(cu);
            try {
                Files.write(javaFile.toPath(), modifiedContent.getBytes());
            } catch (Exception e) {
                throw new RuntimeException("Failed to write file " + javaFile, e);
            }
        }
        
        return changes;
    }
    
    private static class ConstructorFixVisitor extends ModifierVisitor<Void> {
        private final String targetClassName;
        private final int oldParamCount;
        private final int newParamCount;
        private final java.util.Set<String> subclassNames;
        private int changeCount = 0;
        
        public ConstructorFixVisitor(String targetClassName, int oldParamCount, int newParamCount, java.util.Set<String> subclassNames) {
            this.targetClassName = targetClassName;
            this.oldParamCount = oldParamCount;
            this.newParamCount = newParamCount;
            this.subclassNames = subclassNames;
        }
        
        public int getChangeCount() {
            return changeCount;
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call for the target class or its subclasses
            String typeName = n.getType().asString();
            
            // Extract simple name from fully-qualified class name
            String simpleName = targetClassName.substring(targetClassName.lastIndexOf('.') + 1);
            
            // Check if it's the target class or one of its subclasses
            if (simpleName.equals(typeName) || subclassNames.contains(typeName)) {
                NodeList<Expression> arguments = n.getArguments();
                if (arguments.size() == oldParamCount) {
                    System.out.println("  Found " + oldParamCount + "-argument " + typeName + " constructor call");
                    
                    // Remove extra arguments (from newParamCount to oldParamCount-1)
                    for (int i = oldParamCount - 1; i >= newParamCount; i--) {
                        arguments.remove(i);
                    }
                    changeCount++;
                    System.out.println("  Removed " + (oldParamCount - newParamCount) + " argument(s)");
                }
            }
            
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ConstructorDeclaration n, Void arg) {
            // Check if this constructor has the old parameter count
            if (n.getParameters().size() == oldParamCount) {
                // Check the body for a super call
                if (n.getBody().getStatements().size() > 0) {
                    Statement firstStmt = n.getBody().getStatements().get(0);
                    if (firstStmt instanceof ExplicitConstructorInvocationStmt) {
                        ExplicitConstructorInvocationStmt superCall = (ExplicitConstructorInvocationStmt) firstStmt;
                        
                        // Check if super call has oldParamCount arguments
                        if (superCall.getArguments().size() == oldParamCount) {
                            System.out.println("  Found constructor with " + oldParamCount + "-parameter super call");
                            
                            // Remove extra parameters from constructor
                            for (int i = oldParamCount - 1; i >= newParamCount; i--) {
                                n.getParameters().remove(i);
                            }
                            
                            // Remove extra arguments from super call
                            for (int i = oldParamCount - 1; i >= newParamCount; i--) {
                                superCall.getArguments().remove(i);
                            }
                            
                            changeCount++;
                            System.out.println("  Removed " + (oldParamCount - newParamCount) + " parameter(s) and argument(s)");
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
    }
}