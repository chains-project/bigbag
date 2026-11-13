package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

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
 * Generic transformation for Jetty 8/9/10 to Jetty 11 migration.
 * 
 * Breaking changes addressed:
 * 1. javax.servlet -> jakarta.servlet package change
 * 2. Server.setSendServerVersion()/setSendDateHeader() moved to HttpConfiguration
 * 3. SelectChannelConnector deprecated -> ServerConnector
 * 4. Connector API changes
 * 5. AbstractHandler.handle() method signature
 */
public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("Transforms Jetty 8/9/10 code to be compatible with Jetty 11");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(path -> path.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        JavaParser parser = new JavaParser();
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            try {
                Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
                if (!cuOpt.isPresent()) continue;
                
                CompilationUnit cu = cuOpt.get();
                boolean modified = false;
                
                // Track if we're processing a class that uses Jetty
                boolean usesJetty = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().startsWith("org.eclipse.jetty"));
                
                if (usesJetty) {
                    modified |= transformServletImports(cu);
                    modified |= transformJettyApis(cu);
                }
                
                if (modified) {
                    saveFile(cu, javaFile);
                    transformedFiles++;
                    System.out.println("  ✓ " + javaFile.getFileName());
                }
            } catch (Exception e) {
                System.err.println("  ✗ Error: " + javaFile + " - " + e.getMessage());
            }
        }
        
        System.out.println("\nTransformed " + transformedFiles + " files");
        printMigrationGuide();
    }
    
    private static boolean transformServletImports(CompilationUnit cu) {
        boolean modified = false;
        List<ImportDeclaration> toAdd = new ArrayList<>();
        
        for (ImportDeclaration imp : new ArrayList<>(cu.getImports())) {
            if (imp.getNameAsString().startsWith("javax.servlet")) {
                String newName = imp.getNameAsString().replace("javax.servlet", "jakarta.servlet");
                toAdd.add(new ImportDeclaration(newName, imp.isStatic(), imp.isAsterisk()));
                cu.getImports().remove(imp);
                modified = true;
            }
        }
        
        cu.getImports().addAll(toAdd);
        return modified;
    }
    
    private static boolean transformJettyApis(CompilationUnit cu) {
        boolean modified = false;
        
        // Look for Server class usage patterns
        List<ObjectCreationExpr> creations = cu.findAll(ObjectCreationExpr.class);
        for (ObjectCreationExpr creation : creations) {
            if (creation.getTypeAsString().equals("SelectChannelConnector")) {
                modified = transformSelectChannelConnector(cu, creation);
            } else if (creation.getTypeAsString().equals("Server")) {
                modified |= transformServerCreation(cu, creation);
            }
        }
        
        // Look for method calls that need updating
        List<MethodCallExpr> calls = cu.findAll(MethodCallExpr.class);
        for (MethodCallExpr call : calls) {
            String methodName = call.getNameAsString();
            if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                modified = true;
                // These methods should be called on HttpConfiguration, not Server
                // We'll mark them for manual fixing
                call.setName(methodName + " /* TODO: Move to HttpConfiguration */");
            } else if (methodName.equals("setPort") && call.getScope().isPresent()) {
                Expression scope = call.getScope().get();
                if (scope.toString().contains("Connector")) {
                    modified = true;
                    // setPort on Connector - ServerConnector has this method but constructor is better
                    call.setName("setPort /* TODO: Consider constructor parameter */");
                }
            }
        }
        
        // Update AbstractHandler implementations
        modified |= updateAbstractHandlerImplementations(cu);
        
        return modified;
    }
    
    private static boolean transformSelectChannelConnector(CompilationUnit cu, ObjectCreationExpr creation) {
        // Replace: new SelectChannelConnector()
        // With: new ServerConnector(server)
        
        // Add ServerConnector import
        boolean hasImport = cu.getImports().stream()
            .anyMatch(imp -> imp.getNameAsString().equals("org.eclipse.jetty.server.ServerConnector"));
        if (!hasImport) {
            cu.addImport("org.eclipse.jetty.server.ServerConnector");
        }
        
        // Update the creation
        creation.setType(new ClassOrInterfaceType(null, "ServerConnector"));
        
        // ServerConnector needs at least Server as first parameter
        if (creation.getArguments().isEmpty()) {
            creation.addArgument(new NameExpr("server"));
        }
        
        return true;
    }
    
    private static boolean transformServerCreation(CompilationUnit cu, ObjectCreationExpr creation) {
        // When we see new Server(), we should ensure HttpConfiguration is used
        // This is complex to automate fully, so we add comments
        
        // Find the constructor declaration that contains this creation
        Optional<ConstructorDeclaration> constructor = cu.findFirst(ConstructorDeclaration.class);
        if (constructor.isPresent()) {
            BlockStmt body = constructor.get().getBody();
            // Check if we need to add HttpConfiguration setup
            boolean hasHttpConfigSetup = body.findAll(MethodCallExpr.class).stream()
                .anyMatch(call -> call.getNameAsString().contains("HttpConfiguration"));
            
            if (!hasHttpConfigSetup) {
                // Add a comment about HttpConfiguration
                String comment = "// Jetty 11: Server configuration moved to HttpConfiguration\n" +
                               "// HttpConfiguration httpConfig = new HttpConfiguration();\n" +
                               "// httpConfig.setSendServerVersion(false);\n" +
                               "// httpConfig.setSendDateHeader(true);\n" +
                               "// ServerConnector connector = new ServerConnector(server, httpConfig);";
                
                body.addStatement(0, new ExpressionStmt(new NameExpr(comment)));
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean updateAbstractHandlerImplementations(CompilationUnit cu) {
        boolean modified = false;
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
            if (!cls.getExtendedTypes().isEmpty()) {
                String superClass = cls.getExtendedTypes(0).getNameAsString();
                if (superClass.equals("AbstractHandler")) {
                    cls.getMethods().forEach(method -> {
                        if (method.getNameAsString().equals("handle") && method.getParameters().size() == 4) {
                            // Update parameter types
                            for (Parameter param : method.getParameters()) {
                                String type = param.getTypeAsString();
                                if (type.contains("javax.servlet")) {
                                    param.setType(type.replace("javax.servlet", "jakarta.servlet"));
                                }
                            }
                        }
                    });
                }
            }
        });
        
        return modified;
    }
    
    private static void saveFile(CompilationUnit cu, Path path) throws IOException {
        try (FileWriter writer = new FileWriter(path.toFile())) {
            writer.write(cu.toString());
        }
    }
    
    private static void printMigrationGuide() {
        System.out.println("\n=== JETTY 11 MIGRATION GUIDE ===");
        System.out.println("\n1. Package changes:");
        System.out.println("   javax.servlet -> jakarta.servlet");
        System.out.println("\n2. Server configuration:");
        System.out.println("   Old: server.setSendServerVersion(false)");
        System.out.println("   New: HttpConfiguration httpConfig = new HttpConfiguration();");
        System.out.println("        httpConfig.setSendServerVersion(false);");
        System.out.println("\n3. Connectors:");
        System.out.println("   Old: SelectChannelConnector connector = new SelectChannelConnector();");
        System.out.println("   New: ServerConnector connector = new ServerConnector(server, httpConfig);");
        System.out.println("\n4. Handler interface:");
        System.out.println("   Update handle() method parameters to use jakarta.servlet");
        System.out.println("\n5. Manual fixes needed:");
        System.out.println("   - Review TODO comments in transformed code");
        System.out.println("   - Update ServerConnector constructor arguments");
        System.out.println("   - Integrate HttpConfiguration with ServerConnector");
        System.out.println("   - Test thoroughly after migration");
    }
}