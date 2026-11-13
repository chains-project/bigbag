package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generic transformation rule for fixing breaking changes in Apache Commons IO.
 * 
 * This transformation handles:
 * 1. Method renames (e.g., getByteCount() -> getCount())
 * 2. Missing class warnings
 * 3. Configurable class replacements
 * 
 * The transformation is parameterized and can be configured for different
 * breaking changes by modifying the configuration maps.
 */
public class Main {
    
    // Configuration: Method renames
    private static final Map<String, String> METHOD_RENAMES = new HashMap<>();
    static {
        METHOD_RENAMES.put("getByteCount", "getCount");
    }
    
    // Configuration: Classes that may not exist in new API
    private static final Set<String> DEPRECATED_CLASSES = new HashSet<>();
    static {
        DEPRECATED_CLASSES.add("org.apache.commons.io.input.BoundedInputStream");
        DEPRECATED_CLASSES.add("org.apache.commons.io.input.ClosedInputStream");
        DEPRECATED_CLASSES.add("org.apache.commons.io.output.ThresholdingOutputStream");
        DEPRECATED_CLASSES.add("org.apache.commons.io.output.NullPrintStream");
    }
    
    // Configuration: Suggested replacements for deprecated classes
    private static final Map<String, Replacement> CLASS_REPLACEMENTS = new HashMap<>();
    static {
        CLASS_REPLACEMENTS.put("BoundedInputStream", 
            new Replacement("BoundedInputStream", "Consider using a custom implementation or alternative"));
        CLASS_REPLACEMENTS.put("ClosedInputStream",
            new Replacement("ByteArrayInputStream", 
                "ByteArrayInputStream with empty array", 
                Arrays.asList("new ByteArrayInputStream(new byte[0])")));
        CLASS_REPLACEMENTS.put("ThresholdingOutputStream",
            new Replacement("ThresholdingOutputStream", "May need custom implementation"));
        CLASS_REPLACEMENTS.put("NullPrintStream",
            new Replacement("PrintStream", 
                "PrintStream wrapping OutputStream.nullOutputStream()",
                Arrays.asList("new PrintStream(OutputStream.nullOutputStream())")));
    }
    
    static class Replacement {
        final String className;
        final String suggestion;
        final List<String> exampleReplacements;
        
        Replacement(String className, String suggestion) {
            this(className, suggestion, Collections.emptyList());
        }
        
        Replacement(String className, String suggestion, List<String> exampleReplacements) {
            this.className = className;
            this.suggestion = suggestion;
            this.exampleReplacements = exampleReplacements;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        boolean applyFixes = args.length > 1 && args[1].equals("--apply-fixes");
        boolean configMode = args.length > 1 && args[1].equals("--config");
        
        System.out.println("=== Commons IO Breaking Change Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        
        if (configMode) {
            printConfiguration();
            return;
        }
        
        System.out.println("Apply fixes: " + applyFixes);
        System.out.println();
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int totalIssues = 0;
            int filesWithIssues = 0;
            List<Issue> allIssues = new ArrayList<>();
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = StaticJavaParser.parse(javaFile);
                    TransformationVisitor visitor = new TransformationVisitor(applyFixes);
                    CompilationUnit transformed = (CompilationUnit) cu.accept(visitor, null);
                    
                    if (!visitor.getIssues().isEmpty()) {
                        filesWithIssues++;
                        totalIssues += visitor.getIssues().size();
                        allIssues.addAll(visitor.getIssues());
                        
                        System.out.println("\nFile: " + javaFile);
                        for (Issue issue : visitor.getIssues()) {
                            System.out.println("  • " + issue.type + ": " + issue.message);
                            if (issue.suggestion != null && !issue.suggestion.isEmpty()) {
                                System.out.println("    Suggestion: " + issue.suggestion);
                            }
                        }
                        
                        if (applyFixes && visitor.hasChanges()) {
                            PrinterConfiguration config = new DefaultPrinterConfiguration();
                            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
                            String transformedCode = printer.print(transformed);
                            Files.write(javaFile, transformedCode.getBytes());
                            System.out.println("    ✓ Applied fixes");
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
                }
            }
            
            printSummary(filesWithIssues, totalIssues, allIssues, applyFixes);
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void printUsage() {
        System.err.println("Usage: java github.chains.Main <source-directory> [option]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --apply-fixes    Apply automatic fixes where possible");
        System.err.println("  --config         Show current transformation configuration");
        System.err.println();
        System.err.println("Examples:");
        System.err.println("  java github.chains.Main /path/to/src --config");
        System.err.println("  java github.chains.Main /path/to/src");
        System.err.println("  java github.chains.Main /path/to/src --apply-fixes");
    }
    
    private static void printConfiguration() {
        System.out.println("=== Transformation Configuration ===");
        
        System.out.println("\nMethod Renames:");
        for (Map.Entry<String, String> entry : METHOD_RENAMES.entrySet()) {
            System.out.println("  " + entry.getKey() + "() -> " + entry.getValue() + "()");
        }
        
        System.out.println("\nDeprecated Classes (may not exist in new API):");
        for (String cls : DEPRECATED_CLASSES) {
            String simpleName = cls.substring(cls.lastIndexOf('.') + 1);
            Replacement replacement = CLASS_REPLACEMENTS.get(simpleName);
            System.out.println("  " + cls);
            if (replacement != null) {
                System.out.println("    Suggested: " + replacement.className);
                System.out.println("    Note: " + replacement.suggestion);
                if (!replacement.exampleReplacements.isEmpty()) {
                    System.out.println("    Example: " + replacement.exampleReplacements.get(0));
                }
            }
        }
    }
    
    private static void printSummary(int filesWithIssues, int totalIssues, List<Issue> allIssues, boolean applyFixes) {
        System.out.println("\n=== Summary ===");
        System.out.println("Files with issues: " + filesWithIssues);
        System.out.println("Total issues found: " + totalIssues);
        
        if (!allIssues.isEmpty()) {
            System.out.println("\nIssue Breakdown:");
            Map<String, Long> issueCounts = allIssues.stream()
                .collect(Collectors.groupingBy(i -> i.type, Collectors.counting()));
            
            for (Map.Entry<String, Long> entry : issueCounts.entrySet()) {
                System.out.println("  " + entry.getKey() + ": " + entry.getValue());
            }
        }
        
        if (!applyFixes && totalIssues > 0) {
            System.out.println("\nTo apply automatic fixes, run with --apply-fixes");
            System.out.println("Note: Some fixes may require manual review and adjustment.");
        }
    }
    
    static class Issue {
        final String type;
        final String message;
        final String suggestion;
        
        Issue(String type, String message) {
            this(type, message, null);
        }
        
        Issue(String type, String message, String suggestion) {
            this.type = type;
            this.message = message;
            this.suggestion = suggestion;
        }
    }
    
    static class TransformationVisitor extends ModifierVisitor<Void> {
        private final boolean applyFixes;
        private final List<Issue> issues = new ArrayList<>();
        private boolean hasChanges = false;
        
        public TransformationVisitor(boolean applyFixes) {
            this.applyFixes = applyFixes;
        }
        
        public List<Issue> getIssues() {
            return issues;
        }
        
        public boolean hasChanges() {
            return hasChanges;
        }
        
        @Override
        public Node visit(ImportDeclaration id, Void arg) {
            String importName = id.getNameAsString();
            
            if (DEPRECATED_CLASSES.contains(importName)) {
                String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
                Replacement replacement = CLASS_REPLACEMENTS.get(simpleName);
                
                String suggestion = null;
                if (replacement != null && !replacement.className.equals(simpleName)) {
                    suggestion = "Change import to: " + replacement.className;
                    
                    if (applyFixes) {
                        id.setName(replacement.className.contains(".") ? 
                            replacement.className : "java.io." + replacement.className);
                        hasChanges = true;
                    }
                }
                
                issues.add(new Issue("Deprecated Import", 
                    "Import '" + importName + "' may not exist in new Commons IO API",
                    suggestion));
            }
            
            return (Node) super.visit(id, arg);
        }
        
        @Override
        public Node visit(MethodCallExpr n, Void arg) {
            String methodName = n.getNameAsString();
            
            if (METHOD_RENAMES.containsKey(methodName)) {
                String newMethodName = METHOD_RENAMES.get(methodName);
                String suggestion = "Change to: " + newMethodName + "()";
                
                issues.add(new Issue("Method Rename",
                    "Method '" + methodName + "()' may have been renamed to '" + newMethodName + "()'",
                    suggestion));
                
                if (applyFixes) {
                    n.setName(newMethodName);
                    hasChanges = true;
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ClassOrInterfaceType n, Void arg) {
            String typeName = n.getNameAsString();
            
            if (CLASS_REPLACEMENTS.containsKey(typeName)) {
                Replacement replacement = CLASS_REPLACEMENTS.get(typeName);
                
                String suggestion = null;
                if (!replacement.className.equals(typeName)) {
                    suggestion = "Change type to: " + replacement.className;
                    
                    if (applyFixes) {
                        n.setName(replacement.className);
                        hasChanges = true;
                    }
                }
                
                issues.add(new Issue("Deprecated Type",
                    "Type '" + typeName + "' may not exist in new Commons IO API",
                    suggestion != null ? suggestion : replacement.suggestion));
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(ObjectCreationExpr n, Void arg) {
            if (n.getType() instanceof ClassOrInterfaceType) {
                String typeName = ((ClassOrInterfaceType) n.getType()).getNameAsString();
                
                if (CLASS_REPLACEMENTS.containsKey(typeName)) {
                    Replacement replacement = CLASS_REPLACEMENTS.get(typeName);
                    
                    if (!replacement.exampleReplacements.isEmpty() && applyFixes) {
                        String example = replacement.exampleReplacements.get(0);
                        issues.add(new Issue("Object Creation",
                            "Creating instance of '" + typeName + "' which may not exist",
                            "Replace with: " + example));
                        // Note: Auto-replacing object creation is complex and context-dependent
                        // For a generic transformation, we just warn
                    } else {
                        issues.add(new Issue("Object Creation",
                            "Creating instance of '" + typeName + "' which may not exist",
                            replacement.suggestion));
                    }
                }
            }
            
            return (Node) super.visit(n, arg);
        }
        
        @Override
        public Node visit(FieldAccessExpr n, Void arg) {
            if (n.getNameAsString().equals("NULL_PRINT_STREAM")) {
                if (n.getScope() instanceof NameExpr && 
                    ((NameExpr) n.getScope()).getNameAsString().equals("NullPrintStream")) {
                    
                    issues.add(new Issue("Field Access",
                        "NullPrintStream.NULL_PRINT_STREAM may not exist in new Commons IO API",
                        "Replace with: new PrintStream(OutputStream.nullOutputStream())"));
                }
            }
            
            return (Node) super.visit(n, arg);
        }
    }
}