package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    // Configuration: Parameterizable for different breaking changes
    // These could be read from command line arguments or config file
    private static final String TARGET_CLASS = "com.opensymphony.xwork2.ActionContext";
    private static final String GET_METHOD = "getParameters";
    private static final String SET_METHOD = "setParameters";
    private static final String OLD_TYPE = "java.util.Map";
    private static final String NEW_TYPE = "org.apache.struts2.dispatcher.HttpParameters";
    private static final String NEW_TYPE_SIMPLE = "HttpParameters";
    // Additional pattern: method calls that need wrapping
    private static final String WRAP_PATTERN = "HttpParameters.create($ARG).build()";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <sourceDirectory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("=== Struts2 API Migration Tool ===");
        System.out.println("Transforming source directory: " + sourceDir);
        System.out.println("Breaking Change: " + TARGET_CLASS + "." + GET_METHOD + "()");
        System.out.println("  Old return type: " + OLD_TYPE);
        System.out.println("  New return type: " + NEW_TYPE);
        System.out.println("Breaking Change: " + TARGET_CLASS + "." + SET_METHOD + "()");
        System.out.println("  Old parameter: " + OLD_TYPE);
        System.out.println("  New parameter: " + NEW_TYPE);
        System.out.println("===================================");
        
        try {
            transformProject(Paths.get(sourceDir));
            System.out.println("\nTransformation completed successfully.");
            System.out.println("Summary of changes needed:");
            System.out.println("1. Variable types storing " + GET_METHOD + "() result should change from " + OLD_TYPE + " to " + NEW_TYPE);
            System.out.println("2. Method return types that return " + GET_METHOD + "() should change from " + OLD_TYPE + " to " + NEW_TYPE);
            System.out.println("3. Calls to " + SET_METHOD + "(Map) should be changed to " + SET_METHOD + "(HttpParameters.create(map).build())");
            System.out.println("4. Add import for " + NEW_TYPE + " if not already present");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws Exception {
        List<File> javaFiles = findJavaFiles(sourceDir.toFile());
        JavaParser parser = new JavaParser();
        int filesModified = 0;
        int issuesFound = 0;
        
        for (File file : javaFiles) {
            Optional<CompilationUnit> cuOpt = parser.parse(file).getResult();
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                boolean modified = false;
                List<String> changes = new ArrayList<>();
                
                // Check if import for NEW_TYPE is needed
                boolean hasImport = cu.getImports().stream()
                    .anyMatch(imp -> imp.getNameAsString().equals(NEW_TYPE));
                if (!hasImport) {
                    changes.add("Add import: " + NEW_TYPE);
                    modified = true;
                }
                
                // Rule 1: Fix variable types for getMethod() calls
                List<VariableDeclarator> vars = cu.findAll(VariableDeclarator.class);
                for (VariableDeclarator var : vars) {
                    if (var.getInitializer().isPresent()) {
                        Node initializer = var.getInitializer().get();
                        if (initializer instanceof MethodCallExpr) {
                            MethodCallExpr call = (MethodCallExpr) initializer;
                            if (isTargetGetMethodCall(call)) {
                                Type varType = var.getType();
                                if (isOldType(varType)) {
                                    changes.add("Change variable '" + var.getNameAsString() + "' type from " + varType + " to " + NEW_TYPE_SIMPLE);
                                    modified = true;
                                    issuesFound++;
                                }
                            }
                        }
                    }
                }
                
                // Rule 2: Fix method return types that call getMethod()
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                for (MethodDeclaration method : methods) {
                    Type returnType = method.getType();
                    if (isOldType(returnType) && method.getBody().isPresent()) {
                        boolean hasGetCall = method.getBody().get().findAll(MethodCallExpr.class).stream()
                            .anyMatch(Main::isTargetGetMethodCall);
                        if (hasGetCall) {
                            changes.add("Change method '" + method.getNameAsString() + "' return type from " + returnType + " to " + NEW_TYPE_SIMPLE);
                            modified = true;
                            issuesFound++;
                        }
                    }
                }
                
                // Rule 3: Find setMethod() calls that need transformation
                List<MethodCallExpr> methodCalls = cu.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : methodCalls) {
                    if (isTargetSetMethodCall(call)) {
                        if (call.getArguments().size() == 1) {
                            changes.add("Transform call: " + call + " -> " + SET_METHOD + "(HttpParameters.create(...).build())");
                            modified = true;
                            issuesFound++;
                        }
                    }
                }
                
                // Rule 4: Find casts of getMethod() results
                List<MethodCallExpr> allCalls = cu.findAll(MethodCallExpr.class);
                for (MethodCallExpr call : allCalls) {
                    if (isTargetGetMethodCall(call) && call.getParentNode().isPresent()) {
                        Node parent = call.getParentNode().get();
                        String parentStr = parent.toString();
                        if (parentStr.contains("(Map)") || parentStr.contains("(java.util.Map)")) {
                            changes.add("Remove unnecessary cast of " + GET_METHOD + "() result to Map");
                            modified = true;
                            issuesFound++;
                        }
                    }
                }
                
                if (modified) {
                    filesModified++;
                    System.out.println("\nFile: " + file);
                    for (String change : changes) {
                        System.out.println("  - " + change);
                    }
                }
            }
        }
        
        System.out.println("\n=== Summary ===");
        System.out.println("Files processed: " + javaFiles.size());
        System.out.println("Files needing changes: " + filesModified);
        System.out.println("Total issues found: " + issuesFound);
    }
    
    private static boolean isTargetGetMethodCall(MethodCallExpr call) {
        if (!call.getNameAsString().equals(GET_METHOD)) {
            return false;
        }
        
        // Check scope - look for ActionContext or similar
        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            String targetSimpleName = TARGET_CLASS.substring(TARGET_CLASS.lastIndexOf('.') + 1);
            return scope.contains(targetSimpleName) ||
                   scope.contains("getContext()") || 
                   scope.contains("getActionContext()") ||
                   scope.contains("actionContext");
        }
        
        return false;
    }
    
    private static boolean isTargetSetMethodCall(MethodCallExpr call) {
        if (!call.getNameAsString().equals(SET_METHOD)) {
            return false;
        }
        
        // Check scope
        if (call.getScope().isPresent()) {
            String scope = call.getScope().get().toString();
            String targetSimpleName = TARGET_CLASS.substring(TARGET_CLASS.lastIndexOf('.') + 1);
            return scope.contains(targetSimpleName) ||
                   scope.contains("getContext()") || 
                   scope.contains("getActionContext()") ||
                   scope.contains("actionContext");
        }
        
        return false;
    }
    
    private static boolean isOldType(Type type) {
        String typeStr = type.toString();
        return typeStr.contains("Map") && !typeStr.contains(NEW_TYPE_SIMPLE) && !typeStr.contains("Map<");
    }
    
    private static ClassOrInterfaceType createNewType() {
        return new ClassOrInterfaceType(null, NEW_TYPE_SIMPLE);
    }
    
    private static List<File> findJavaFiles(File dir) {
        List<File> javaFiles = new ArrayList<>();
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        javaFiles.addAll(findJavaFiles(file));
                    } else if (file.getName().endsWith(".java")) {
                        javaFiles.add(file);
                    }
                }
            }
        }
        return javaFiles;
    }
}