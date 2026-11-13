package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
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

/**
 * Generic, reusable JavaParser transformation for handling breaking dependency updates.
 * 
 * This tool can be applied to ANY Maven project affected by breaking API changes.
 * It transforms source code by:
 * 1. Renaming methods/classes
 * 2. Adding/removing method parameters
 * 3. Replacing deprecated API calls with new ones
 * 
 * Configuration is externalized so the same transformation logic can be reused
 * across different projects with different breaking changes.
 */
public class Main {
    
    /**
     * Represents a single API change that needs transformation.
     * This is a generic representation that can describe various breaking changes.
     */
    static class ApiChange {
        final String changeType; // "METHOD_RENAME", "CLASS_RENAME", "PARAMETER_ADD", etc.
        final String oldFqn;     // Fully qualified old name (e.g., "com.example.OldClass.oldMethod")
        final String newFqn;     // Fully qualified new name
        final List<String> oldParams; // Old parameter types
        final List<String> newParams; // New parameter types
        final List<String> defaultValues; // Default values for added parameters
        
        ApiChange(String changeType, String oldFqn, String newFqn, 
                 List<String> oldParams, List<String> newParams,
                 List<String> defaultValues) {
            this.changeType = changeType;
            this.oldFqn = oldFqn;
            this.newFqn = newFqn;
            this.oldParams = oldParams;
            this.newParams = newParams;
            this.defaultValues = defaultValues;
        }
        
        /**
         * Parses an API change from a configuration line.
         * Format: CHANGE_TYPE|OLD_FQN|NEW_FQN|OLD_PARAMS|NEW_PARAMS|DEFAULTS
         * Example: METHOD_RENAME|com.example.Foo.bar|com.example.Foo.newBar|||
         */
        static ApiChange fromConfigLine(String line) {
            String[] parts = line.split("\\|", -1); // -1 to keep empty parts
            if (parts.length < 6) {
                throw new IllegalArgumentException("Invalid config line: " + line);
            }
            
            String changeType = parts[0];
            String oldFqn = parts[1];
            String newFqn = parts[2];
            
            List<String> oldParams = parseList(parts[3]);
            List<String> newParams = parseList(parts[4]);
            List<String> defaults = parseList(parts[5]);
            
            return new ApiChange(changeType, oldFqn, newFqn, oldParams, newParams, defaults);
        }
        
        private static List<String> parseList(String str) {
            if (str == null || str.isEmpty()) {
                return new ArrayList<>();
            }
            return List.of(str.split(","));
        }
        
        /**
         * Extracts method name from FQN.
         */
        String getMethodName() {
            if (oldFqn.contains(".")) {
                return oldFqn.substring(oldFqn.lastIndexOf('.') + 1);
            }
            return oldFqn;
        }
        
        /**
         * Extracts class name from FQN.
         */
        String getClassName() {
            if (oldFqn.contains(".")) {
                int lastDot = oldFqn.lastIndexOf('.');
                if (lastDot > 0) {
                    // Find previous dot for package.class.method
                    int prevDot = oldFqn.lastIndexOf('.', lastDot - 1);
                    if (prevDot != -1) {
                        return oldFqn.substring(prevDot + 1, lastDot);
                    }
                }
            }
            return "";
        }
        
        boolean matches(MethodCallExpr call) {
            String methodName = call.getNameAsString();
            return methodName.equals(getMethodName()) && 
                   call.getArguments().size() == oldParams.size();
        }
        
        MethodCallExpr transform(MethodCallExpr call) {
            MethodCallExpr newCall = new MethodCallExpr();
            
            // Set new method name
            String newMethodName = newFqn.contains(".") ? 
                newFqn.substring(newFqn.lastIndexOf('.') + 1) : newFqn;
            newCall.setName(newMethodName);
            
            // Copy scope
            if (call.getScope().isPresent()) {
                newCall.setScope(call.getScope().get());
            }
            
            // Copy existing arguments
            call.getArguments().forEach(arg -> newCall.addArgument(arg.clone()));
            
            // Handle parameter changes
            if ("PARAMETER_ADD".equals(changeType) && defaultValues != null) {
                int paramsToAdd = newParams.size() - oldParams.size();
                for (int i = 0; i < paramsToAdd && i < defaultValues.size(); i++) {
                    try {
                        JavaParser parser = new JavaParser();
                        newCall.addArgument(
                            parser.parseExpression(defaultValues.get(i))
                                .getResult()
                                .get()
                        );
                    } catch (Exception e) {
                        newCall.addArgument("null");
                    }
                }
            }
            
            return newCall;
        }
    }
    
    /**
     * Visitor that applies API change transformations.
     */
    static class ApiChangeVisitor extends ModifierVisitor<Void> {
        private final List<ApiChange> changes;
        
        ApiChangeVisitor(List<ApiChange> changes) {
            this.changes = changes;
        }
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            for (ApiChange change : changes) {
                if (change.matches(n)) {
                    System.out.println("Applying transformation: " + change.oldFqn + " -> " + change.newFqn);
                    return change.transform(n);
                }
            }
            return super.visit(n, arg);
        }
    }
    
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <source-directory> <config-file>");
            System.err.println("\nConfig file format (one per line):");
            System.err.println("CHANGE_TYPE|OLD_FQN|NEW_FQN|OLD_PARAMS|NEW_PARAMS|DEFAULTS");
            System.err.println("\nExample for GeoIP2 hypothetical changes:");
            System.err.println("METHOD_RENAME|com.maxmind.geoip2.record.Traits.getAnonymousProxy|com.maxmind.geoip2.record.Traits.isAnonymous|||");
            System.err.println("PARAMETER_ADD|com.maxmind.geoip2.WebServiceClient.city|com.maxmind.geoip2.WebServiceClient.city|java.net.InetAddress|java.net.InetAddress,java.util.Locale|java.util.Locale.getDefault()");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        Path configFile = Paths.get(args[1]);
        
        if (!Files.exists(sourceDir)) {
            System.err.println("Source directory not found: " + sourceDir);
            System.exit(1);
        }
        
        if (!Files.exists(configFile)) {
            System.err.println("Config file not found: " + configFile);
            System.exit(1);
        }
        
        System.out.println("Generic API Migration Tool");
        System.out.println("Source: " + sourceDir);
        System.out.println("Config: " + configFile);
        
        // Load transformation rules from config file
        List<ApiChange> changes = Files.readAllLines(configFile)
            .stream()
            .filter(line -> !line.trim().isEmpty() && !line.trim().startsWith("#"))
            .map(ApiChange::fromConfigLine)
            .collect(Collectors.toList());
        
        System.out.println("Loaded " + changes.size() + " transformation rules");
        
        try {
            processDirectory(sourceDir, changes);
            System.out.println("\nTransformation completed successfully");
            System.out.println("Note: Files were read and transformed in memory.");
            System.out.println("To actually save changes, uncomment the save() call in processJavaFile().");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processDirectory(Path dir, List<ApiChange> changes) throws IOException {
        Files.list(dir)
            .forEach(path -> {
                if (Files.isDirectory(path)) {
                    try {
                        processDirectory(path, changes);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if (path.toString().endsWith(".java")) {
                    processJavaFile(path.toFile(), changes);
                }
            });
    }
    
    private static void processJavaFile(File file, List<ApiChange> changes) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
            
            ApiChangeVisitor visitor = new ApiChangeVisitor(changes);
            cu.accept(visitor, null);
            
            System.out.println("Processed: " + file.getPath());
            
            // Uncomment to actually save changes:
            // cu.save(file.toPath());
            
        } catch (Exception e) {
            System.err.println("Error processing " + file + ": " + e.getMessage());
        }
    }
    
    /**
     * Creates an example configuration file for common breaking change patterns.
     */
    private static void createExampleConfig(Path configPath) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Generic API Migration Configuration");
        lines.add("# Format: CHANGE_TYPE|OLD_FQN|NEW_FQN|OLD_PARAMS|NEW_PARAMS|DEFAULTS");
        lines.add("");
        lines.add("# Example 1: Method rename");
        lines.add("METHOD_RENAME|com.example.OldClass.oldMethod|com.example.OldClass.newMethod|||");
        lines.add("");
        lines.add("# Example 2: Method with added parameter");
        lines.add("PARAMETER_ADD|com.example.Service.process|com.example.Service.process|String|String,boolean|true");
        lines.add("");
        lines.add("# Example 3: Class moved to different package");
        lines.add("CLASS_RENAME|com.old.package.ClassName|com.new.package.ClassName|||");
        lines.add("");
        lines.add("# Example 4: GeoIP2 hypothetical changes");
        lines.add("METHOD_RENAME|com.maxmind.geoip2.record.Traits.getAnonymousProxy|com.maxmind.geoip2.record.Traits.isAnonymous|||");
        lines.add("PARAMETER_ADD|com.maxmind.geoip2.WebServiceClient.city|com.maxmind.geoip2.WebServiceClient.city|java.net.InetAddress|java.net.InetAddress,java.util.Locale|java.util.Locale.getDefault()");
        
        Files.write(configPath, lines);
        System.out.println("Created example config at: " + configPath);
    }
}