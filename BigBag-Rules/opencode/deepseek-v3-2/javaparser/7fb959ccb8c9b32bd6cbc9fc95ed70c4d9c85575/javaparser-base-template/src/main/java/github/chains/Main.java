package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for Jetty 9/10 to Jetty 11 migration.
 * 
 * This transformation handles the breaking changes between Jetty 9/10 and Jetty 11:
 * 
 * 1. Servlet API migration: javax.servlet -> jakarta.servlet
 * 2. SelectChannelConnector -> ServerConnector (different package)
 * 3. Removed methods on Server class (setSendServerVersion, setSendDateHeader)
 * 4. Removed setPort() method on Connector interface
 * 5. Updated AbstractHandler.handle() method signature
 * 
 * The transformation is generic and can be applied to any Java project
 * migrating from Jetty 9/10 to Jetty 11.
 */
public class Main {
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar jetty11-migration.jar <source-directory>");
            System.err.println("\nThis transformation handles Jetty 9/10 to Jetty 11 breaking changes:");
            System.err.println("• javax.servlet -> jakarta.servlet (all imports and type references)");
            System.err.println("• SelectChannelConnector -> ServerConnector (class and imports)");
            System.err.println("• Server configuration via HttpConfiguration instead of direct methods");
            System.err.println("• Constructor and method signature updates");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("⚡ Jetty 9/10 to Jetty 11 Migration Transformer");
        System.out.println("==============================================");
        System.out.println("Source directory: " + sourceDir.toAbsolutePath());
        System.out.println();
        
        try {
            List<Path> javaFiles = Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("📁 Found " + javaFiles.size() + " Java files to process");
            System.out.println();
            
            int transformedCount = 0;
            int warningCount = 0;
            
            for (Path javaFile : javaFiles) {
                TransformationResult result = transformFile(javaFile);
                if (result.transformed) {
                    transformedCount++;
                }
                warningCount += result.warnings;
            }
            
            System.out.println();
            System.out.println("✅ Transformation Complete!");
            System.out.println("==========================");
            System.out.println("Files transformed: " + transformedCount + " / " + javaFiles.size());
            System.out.println("Warnings generated: " + warningCount);
            System.out.println();
            System.out.println("📋 Manual Review Required:");
            System.out.println("-------------------------");
            System.out.println("The following changes require manual code updates:");
            System.out.println();
            System.out.println("1. ServerConnector Construction:");
            System.out.println("   Old: new SelectChannelConnector()");
            System.out.println("   New: new ServerConnector(server) // Requires Server instance");
            System.out.println();
            System.out.println("2. Server Configuration:");
            System.out.println("   Old: server.setSendServerVersion(false)");
            System.out.println("   New: Configure via HttpConfiguration:");
            System.out.println("        HttpConfiguration config = new HttpConfiguration();");
            System.out.println("        config.setSendServerVersion(false);");
            System.out.println("        ServerConnector connector = new ServerConnector(server, config);");
            System.out.println();
            System.out.println("3. Port Configuration:");
            System.out.println("   Old: connector.setPort(8080)");
            System.out.println("   New: Pass port to ServerConnector constructor:");
            System.out.println("        new ServerConnector(server, null, null, null, -1, -1, new HttpConnectionFactory(config))");
            System.out.println("        // Port is configured via ConnectionFactory or connector methods");
            System.out.println();
            System.out.println("4. Handle Method Signatures:");
            System.out.println("   Old: handle(String, Request, HttpServletRequest, HttpServletResponse)");
            System.out.println("   New: handle(String, Request, HttpServletRequest, HttpServletResponse)");
            System.out.println("        // Only package changed from javax.servlet to jakarta.servlet");
            
        } catch (IOException e) {
            System.err.println("❌ Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static class TransformationResult {
        boolean transformed = false;
        int warnings = 0;
    }
    
    private static TransformationResult transformFile(Path javaFile) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElse(null);
            if (cu == null) {
                System.err.println("  ❌ Failed to parse: " + javaFile.getFileName());
                return new TransformationResult();
            }
        }
        
        TransformationResult result = new TransformationResult();
        String fileName = javaFile.getFileName().toString();
        
        // Track changes to report
        List<String> changes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 1. Transform imports
        boolean importsUpdated = updateImports(cu, fileName, changes, warnings);
        
        // 2. Transform type references in code
        boolean typesUpdated = updateTypeReferences(cu, fileName, changes, warnings);
        
        // 3. Transform method calls and signatures
        boolean methodsUpdated = updateMethodCallsAndSignatures(cu, fileName, changes, warnings);
        
        if (importsUpdated || typesUpdated || methodsUpdated) {
            result.transformed = true;
            result.warnings = warnings.size();
            
            // Write changes
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
            
            // Print summary for this file
            if (!changes.isEmpty() || !warnings.isEmpty()) {
                System.out.println("\n📄 " + fileName);
                if (!changes.isEmpty()) {
                    System.out.println("  Applied changes:");
                    for (String change : changes) {
                        System.out.println("    • " + change);
                    }
                }
                if (!warnings.isEmpty()) {
                    System.out.println("  ⚠️  Warnings (require manual fix):");
                    for (String warning : warnings) {
                        System.out.println("    • " + warning);
                    }
                }
            }
        }
        
        return result;
    }
    
    private static boolean updateImports(CompilationUnit cu, String fileName, 
                                         List<String> changes, List<String> warnings) {
        boolean modified = false;
        
        for (ImportDeclaration importDecl : cu.getImports()) {
            String importName = importDecl.getNameAsString();
            
            // javax.servlet -> jakarta.servlet
            if (importName.startsWith("javax.servlet")) {
                String newImport = importName.replace("javax.servlet", "jakarta.servlet");
                importDecl.setName(newImport);
                changes.add("Updated import: " + importName + " -> " + newImport);
                modified = true;
            }
            
            // SelectChannelConnector import update
            if (importName.equals("org.eclipse.jetty.server.nio.SelectChannelConnector")) {
                // Remove old import
                cu.remove(importDecl);
                // Add new import if not already present
                if (!hasImport(cu, "org.eclipse.jetty.server.ServerConnector")) {
                    cu.addImport("org.eclipse.jetty.server.ServerConnector");
                }
                changes.add("Updated import: SelectChannelConnector -> ServerConnector (different package)");
                modified = true;
            }
        }
        
        return modified;
    }
    
    private static boolean updateTypeReferences(CompilationUnit cu, String fileName,
                                                List<String> changes, List<String> warnings) {
        class TypeVisitor extends VoidVisitorAdapter<Void> {
            boolean modified = false;
            
            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                super.visit(type, arg);
                if (type.getNameAsString().equals("SelectChannelConnector")) {
                    type.setName("ServerConnector");
                    changes.add("Updated type reference: SelectChannelConnector -> ServerConnector");
                    modified = true;
                }
            }
            
            @Override
            public void visit(ObjectCreationExpr expr, Void arg) {
                super.visit(expr, arg);
                if (expr.getType().getNameAsString().equals("SelectChannelConnector")) {
                    expr.getType().setName("ServerConnector");
                    changes.add("Updated constructor call: new SelectChannelConnector() -> new ServerConnector()");
                    
                    // Check if Server parameter is needed
                    if (expr.getArguments().isEmpty()) {
                        warnings.add("ServerConnector constructor requires Server parameter: new ServerConnector(server)");
                    }
                    modified = true;
                }
            }
        }
        
        TypeVisitor visitor = new TypeVisitor();
        cu.accept(visitor, null);
        return visitor.modified;
    }
    
    private static boolean updateMethodCallsAndSignatures(CompilationUnit cu, String fileName,
                                                          List<String> changes, List<String> warnings) {
        class MethodVisitor extends VoidVisitorAdapter<Void> {
            boolean modified = false;
            
            @Override
            public void visit(MethodCallExpr expr, Void arg) {
                super.visit(expr, arg);
                
                String methodName = expr.getNameAsString();
                Optional<Expression> scope = expr.getScope();
                
                // Check for removed Server methods
                if ((methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) 
                    && scope.isPresent()) {
                    String scopeStr = scope.get().toString();
                    if (scopeStr.contains("server") || scopeStr.matches(".*[Ss]erver.*")) {
                        warnings.add(methodName + "() removed from Server in Jetty 11. Configure via HttpConfiguration.");
                    }
                }
                
                // Check for removed Connector.setPort()
                if (methodName.equals("setPort") && scope.isPresent()) {
                    String scopeStr = scope.get().toString();
                    if (scopeStr.matches(".*[Cc]onnector.*") || scopeStr.contains("httpConnector")) {
                        warnings.add("setPort() removed from Connector in Jetty 11. Configure port via ServerConnector constructor or ConnectionFactory.");
                    }
                }
            }
            
            @Override
            public void visit(ClassOrInterfaceDeclaration classDecl, Void arg) {
                super.visit(classDecl, arg);
                
                // Check for AbstractHandler subclasses
                boolean isAbstractHandler = classDecl.getExtendedTypes().stream()
                    .anyMatch(type -> type.getNameAsString().equals("AbstractHandler"));
                
                if (isAbstractHandler) {
                    for (MethodDeclaration method : classDecl.getMethods()) {
                        if (method.getNameAsString().equals("handle")) {
                            // Update parameter types from javax.servlet to jakarta.servlet
                            for (Parameter param : method.getParameters()) {
                                Type paramType = param.getType();
                                String typeStr = paramType.asString();
                                if (typeStr.contains("javax.servlet")) {
                                    String newType = typeStr.replace("javax.servlet", "jakarta.servlet");
                                    param.setType(newType);
                                    changes.add("Updated handle() method parameter: " + typeStr + " -> " + newType);
                                    modified = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        
        MethodVisitor visitor = new MethodVisitor();
        cu.accept(visitor, null);
        return visitor.modified;
    }
    
    private static boolean hasImport(CompilationUnit cu, String importName) {
        return cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals(importName));
    }
}