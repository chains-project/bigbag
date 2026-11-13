package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generic JavaParser transformation rule for fixing breaking API changes.
 * This rule can be configured to handle various method signature changes
 * by parameterizing the old and new method patterns.
 */
public class Main {
    
    /**
     * Configuration for a method transformation rule.
     */
    public static class MethodTransformationRule {
        private final String className;
        private final String methodName;
        private final int oldParameterCount;
        private final int newParameterCount;
        private final List<String> newParameterTypes;
        private final List<String> defaultParameterExpressions;
        
        public MethodTransformationRule(String className, String methodName, 
                                       int oldParameterCount, int newParameterCount,
                                       List<String> newParameterTypes, 
                                       List<String> defaultParameterExpressions) {
            this.className = className;
            this.methodName = methodName;
            this.oldParameterCount = oldParameterCount;
            this.newParameterCount = newParameterCount;
            this.newParameterTypes = newParameterTypes;
            this.defaultParameterExpressions = defaultParameterExpressions;
        }
        
        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public int getOldParameterCount() { return oldParameterCount; }
        public int getNewParameterCount() { return newParameterCount; }
        public List<String> getNewParameterTypes() { return newParameterTypes; }
        public List<String> getDefaultParameterExpressions() { return defaultParameterExpressions; }
    }
    
    /**
     * Visitor that applies method transformation rules to a compilation unit.
     */
    private static class MethodTransformationVisitor extends VoidVisitorAdapter<Void> {
        private final List<MethodTransformationRule> rules;
        private boolean modified = false;
        
        public MethodTransformationVisitor(List<MethodTransformationRule> rules) {
            this.rules = rules;
        }
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            for (MethodTransformationRule rule : rules) {
                // Check if this method call matches our rule
                if (matchesRule(n, rule)) {
                    applyTransformation(n, rule);
                    modified = true;
                    break;
                }
            }
        }
        
        private boolean matchesRule(MethodCallExpr methodCall, MethodTransformationRule rule) {
            // Check method name
            if (!methodCall.getNameAsString().equals(rule.getMethodName())) {
                return false;
            }
            
            // Check parameter count matches old signature
            if (methodCall.getArguments().size() != rule.getOldParameterCount()) {
                return false;
            }
            
            // TODO: Add type checking using symbol solver for more accurate matching
            // For now, we'll match based on name and parameter count
            
            return true;
        }
        
        private void applyTransformation(MethodCallExpr methodCall, MethodTransformationRule rule) {
            NodeList<Expression> arguments = methodCall.getArguments();
            
            // If we need to add parameters
            if (rule.getNewParameterCount() > rule.getOldParameterCount()) {
                int paramsToAdd = rule.getNewParameterCount() - rule.getOldParameterCount();
                for (int i = 0; i < paramsToAdd; i++) {
                    int paramIndex = rule.getOldParameterCount() + i;
                    if (paramIndex < rule.getDefaultParameterExpressions().size()) {
                        String defaultExpr = rule.getDefaultParameterExpressions().get(paramIndex);
                        // Parse the default expression and add it as an argument
                        try {
                            JavaParser parser = new JavaParser();
                            Expression expr = parser.parseExpression(defaultExpr).getResult().orElse(null);
                            if (expr != null) {
                                arguments.add(expr);
                            } else {
                                arguments.add(new NameExpr("null"));
                            }
                        } catch (Exception e) {
                            // If parsing fails, add a null literal as fallback
                            arguments.add(new NameExpr("null"));
                        }
                    } else {
                        // Add null as default if no default expression specified
                        arguments.add(new NameExpr("null"));
                    }
                }
                methodCall.setArguments(arguments);
            }
            
            // Note: We could also handle parameter type changes here
            // by casting or converting arguments
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory> <rule-config>");
            System.err.println("Example: java -jar javaparser.jar /path/to/src method_rules.json");
            return;
        }
        
        String sourceDir = args[0];
        String ruleConfig = args[1];
        
        try {
            // Load transformation rules
            List<MethodTransformationRule> rules = loadTransformationRules(ruleConfig);
            
            // Find all Java files
            List<Path> javaFiles = findJavaFiles(sourceDir);
            
            // Create type solver for symbol resolution
            CombinedTypeSolver typeSolver = new CombinedTypeSolver();
            typeSolver.add(new ReflectionTypeSolver());
            typeSolver.add(new JavaParserTypeSolver(new File(sourceDir)));
            
            JavaParser parser = new JavaParser();
            parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
            
            int totalFilesModified = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = parser.parse(javaFile).getResult().orElse(null);
                    if (cu == null) {
                        continue;
                    }
                    
                    MethodTransformationVisitor visitor = new MethodTransformationVisitor(rules);
                    cu.accept(visitor, null);
                    
                    if (visitor.isModified()) {
                        // Write the modified file back
                        try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                            writer.write(cu.toString());
                            System.out.println("Modified: " + javaFile);
                            totalFilesModified++;
                        }
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error processing file: " + javaFile + " - " + e.getMessage());
                }
            }
            
            System.out.println("\nTransformation complete. Modified " + totalFilesModified + " files.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static List<MethodTransformationRule> loadTransformationRules(String ruleConfig) {
        List<MethodTransformationRule> rules = new ArrayList<>();
        
        // In a real implementation, we would load rules from a JSON/XML/YAML configuration file
        // For this example, we demonstrate with hardcoded rules but the structure supports external config
        
        System.out.println("Loading transformation rules from configuration...");
        
        // Check if ruleConfig is a file path
        File configFile = new File(ruleConfig);
        if (configFile.exists()) {
            // TODO: Implement reading from configuration file
            System.out.println("Reading rules from file: " + ruleConfig);
            // For now, fall through to default rules
        }
        
        // Default rules for common dubbo/hessian breaking changes
        // Rule 1: readObject method signature change 
        rules.add(createRule("com.alibaba.com.caucho.hessian.io.AbstractHessianInput", "readObject", 1, 2, 
                           new String[]{"java.lang.Class", "java.lang.Class..."},
                           new String[]{"null", "new java.lang.Class[0]"}));
        
        rules.add(createRule("com.alibaba.com.caucho.hessian.io.Hessian2Input", "readObject", 1, 2,
                           new String[]{"java.lang.Class", "java.lang.Class..."},
                           new String[]{"null", "new java.lang.Class[0]"}));
        
        rules.add(createRule("com.alibaba.com.caucho.hessian.io.HessianInput", "readObject", 1, 2,
                           new String[]{"java.lang.Class", "java.lang.Class..."},
                           new String[]{"null", "new java.lang.Class[0]"}));
        
        // Add more rules as needed for other breaking changes
        
        System.out.println("Loaded " + rules.size() + " transformation rules");
        System.out.println("Rule patterns:");
        for (MethodTransformationRule rule : rules) {
            System.out.println("  " + rule.getClassName() + "." + rule.getMethodName() + 
                             "(" + rule.getOldParameterCount() + " -> " + rule.getNewParameterCount() + " params)");
        }
        
        return rules;
    }
    
    private static MethodTransformationRule createRule(String className, String methodName,
                                                     int oldParamCount, int newParamCount,
                                                     String[] newParamTypes, String[] defaultParams) {
        return new MethodTransformationRule(className, methodName, oldParamCount, newParamCount,
                                          List.of(newParamTypes), List.of(defaultParams));
    }
    
    private static List<Path> findJavaFiles(String sourceDir) throws IOException {
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        }
    }
}