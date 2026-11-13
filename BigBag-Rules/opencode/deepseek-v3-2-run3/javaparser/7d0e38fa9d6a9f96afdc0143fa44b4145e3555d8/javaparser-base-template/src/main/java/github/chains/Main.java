package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    
    // Configuration for API method transformations
    static class ApiMethodConfig {
        final String className;      // e.g., "com.google.api.services.dns.Dns$ManagedZones"
        final String methodName;      // e.g., "create"
        final int oldParamCount;     // e.g., 2 for create(String, ManagedZone)
        final int locationParamPos;  // Position to insert location parameter (0-indexed)
        final String locationDefaultValue; // Default value for location parameter
        
        ApiMethodConfig(String className, String methodName, int oldParamCount, 
                       int locationParamPos, String locationDefaultValue) {
            this.className = className;
            this.methodName = methodName;
            this.oldParamCount = oldParamCount;
            this.locationParamPos = locationParamPos;
            this.locationDefaultValue = locationDefaultValue;
        }
    }
    
    // List of API method transformations needed
    private static final List<ApiMethodConfig> API_METHOD_CONFIGS = new ArrayList<>();
    
    static {
        // Configure all the breaking API changes
        // ManagedZones methods - location parameter inserted at position 1 (after project)
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "create", 2, 1, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "get", 2, 1, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "delete", 2, 1, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "list", 1, 1, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "patch", 3, 1, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ManagedZones", "update", 3, 1, "global"));
            
        // Changes methods - location parameter inserted at position 2 (after project, managedZone)
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$Changes", "create", 3, 2, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$Changes", "get", 3, 2, "global"));
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$Changes", "list", 2, 2, "global"));
            
        // Projects methods - location parameter inserted at position 1 (after project)
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$Projects", "get", 1, 1, "global"));
            
        // ResourceRecordSets methods - location parameter inserted at position 2 (after project, managedZone)
        API_METHOD_CONFIGS.add(new ApiMethodConfig(
            "com.google.api.services.dns.Dns$ResourceRecordSets", "list", 2, 2, "global"));
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int totalChanges = 0;
            JavaParser parser = new JavaParser();
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) continue;
                    
                    MethodCallVisitor visitor = new MethodCallVisitor();
                    cu.accept(visitor, null);
                    
                    if (visitor.getChangeCount() > 0) {
                        // Write changes back to file
                        Files.write(javaFile, cu.toString().getBytes());
                        System.out.println("Modified: " + javaFile + " (" + visitor.getChangeCount() + " changes)");
                        totalChanges += visitor.getChangeCount();
                    }
                } catch (IOException e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("Total changes applied: " + totalChanges);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static class MethodCallVisitor extends ModifierVisitor<Void> {
        private int changeCount = 0;
        
        public int getChangeCount() {
            return changeCount;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this method call matches any of our configured API methods
            for (ApiMethodConfig config : API_METHOD_CONFIGS) {
                if (matchesMethodCall(n, config)) {
                    return transformMethodCall(n, config);
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean matchesMethodCall(MethodCallExpr n, ApiMethodConfig config) {
            // Check if method name matches
            if (!n.getNameAsString().equals(config.methodName)) {
                return false;
            }
            
            // Check if we have the old parameter count
            if (n.getArguments().size() != config.oldParamCount) {
                return false;
            }
            
            // For now, we'll apply transformation to all matching method calls
            // In a more sophisticated version, we could check the type of the receiver
            // to ensure it's the correct class
            
            return true;
        }
        
        private MethodCallExpr transformMethodCall(MethodCallExpr n, ApiMethodConfig config) {
            changeCount++;
            
            // Create new arguments list with location parameter inserted
            NodeList<Expression> newArgs = new NodeList<>();
            NodeList<Expression> oldArgs = n.getArguments();
            
            // Copy arguments up to the insertion point
            for (int i = 0; i < config.locationParamPos && i < oldArgs.size(); i++) {
                newArgs.add(oldArgs.get(i));
            }
            
            // Insert location parameter
            newArgs.add(new StringLiteralExpr(config.locationDefaultValue));
            
            // Copy remaining arguments
            for (int i = config.locationParamPos; i < oldArgs.size(); i++) {
                newArgs.add(oldArgs.get(i));
            }
            
            // Create new method call with updated arguments
            MethodCallExpr newMethodCall = new MethodCallExpr();
            if (n.getScope().isPresent()) {
                newMethodCall.setScope(n.getScope().get());
            }
            newMethodCall.setName(n.getName());
            newMethodCall.setArguments(newArgs);
            
            return newMethodCall;
        }
    }
}