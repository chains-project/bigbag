package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    
    // Fully qualified class names and method signatures from the DNS API
    private static final String DNS_CHANGES_CLASS = "com.google.api.services.dns.Dns$Changes";
    private static final String DNS_MANAGED_ZONES_CLASS = "com.google.api.services.dns.Dns$ManagedZones";
    private static final String DNS_RESOURCE_RECORD_SETS_CLASS = "com.google.api.services.dns.Dns$ResourceRecordSets";
    private static final String DNS_PROJECTS_CLASS = "com.google.api.services.dns.Dns$Projects";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            processDirectory(Paths.get(sourceDir));
            System.out.println("Transformation completed successfully!");
        } catch (IOException e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path directory) throws IOException {
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::processFile);
    }
    
    private static void processFile(Path filePath) {
        try {
            String content = Files.readString(filePath);
            CompilationUnit cu = new JavaParser().parse(content).getResult().orElseThrow();
            
            DnsApiLocationParameterVisitor visitor = new DnsApiLocationParameterVisitor();
            cu.accept(visitor, null);
            
            // Check if any modifications were made by examining the visitor state
            // For simplicity, we'll always write the file since we can't easily track modifications
            Files.writeString(filePath, cu.toString());
            System.out.println("Processed: " + filePath);
        } catch (IOException e) {
            System.err.println("Error processing file " + filePath + ": " + e.getMessage());
        }
    }
    
    private static class DnsApiLocationParameterVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is a DNS API method call that needs the location parameter
            if (isDnsApiMethodCall(n)) {
                return addLocationParameter(n);
            }
            return super.visit(n, arg);
        }
        
        private boolean isDnsApiMethodCall(MethodCallExpr methodCall) {
            String methodName = methodCall.getNameAsString();
            
            // Check if this is a method call on a DNS API class
            if (methodCall.getScope().isPresent()) {
                String scope = methodCall.getScope().get().toString();
                
                // Match patterns for DNS API method calls
                // These patterns match the structural patterns, not specific class names
                if (scope.contains("managedZones()") || scope.contains("dns.managedZones()")) {
                    return isManagedZonesMethod(methodName, methodCall.getArguments().size());
                } else if (scope.contains("changes()") || scope.contains("dns.changes()")) {
                    return isChangesMethod(methodName, methodCall.getArguments().size());
                } else if (scope.contains("resourceRecordSets()") || scope.contains("dns.resourceRecordSets()")) {
                    return isResourceRecordSetsMethod(methodName, methodCall.getArguments().size());
                } else if (scope.contains("projects()") || scope.contains("dns.projects()")) {
                    return isProjectsMethod(methodName, methodCall.getArguments().size());
                }
            }
            
            return false;
        }
        
        private boolean isManagedZonesMethod(String methodName, int argCount) {
            switch (methodName) {
                case "create": return argCount == 2; // Old: (projectId, zone)
                case "get": return argCount == 2;    // Old: (projectId, zoneName)
                case "list": return argCount == 1;   // Old: (projectId)
                case "delete": return argCount == 2; // Old: (projectId, zoneName)
                default: return false;
            }
        }
        
        private boolean isChangesMethod(String methodName, int argCount) {
            switch (methodName) {
                case "create": return argCount == 3; // Old: (projectId, zoneName, change)
                case "get": return argCount == 3;    // Old: (projectId, zoneName, changeRequestId)
                case "list": return argCount == 2;   // Old: (projectId, zoneName)
                default: return false;
            }
        }
        
        private boolean isResourceRecordSetsMethod(String methodName, int argCount) {
            switch (methodName) {
                case "list": return argCount == 2; // Old: (projectId, zoneName)
                default: return false;
            }
        }
        
        private boolean isProjectsMethod(String methodName, int argCount) {
            switch (methodName) {
                case "get": return argCount == 1; // Old: (projectId)
                default: return false;
            }
        }
        
        private MethodCallExpr addLocationParameter(MethodCallExpr methodCall) {
            String methodName = methodCall.getNameAsString();
            List<com.github.javaparser.ast.expr.Expression> arguments = methodCall.getArguments();
            
            // Insert "global" as location parameter after projectId
            if (arguments.size() > 0) {
                arguments.add(1, new StringLiteralExpr("global"));
            }
            
            return methodCall;
        }
    }
}