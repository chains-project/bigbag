package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generic JavaParser transformation for breaking dependency updates.
 * 
 * This transformation handles two common breaking change patterns:
 * 
 * 1. StringUtils.isAllBlank() method removal:
 *    - Old: StringUtils.isAllBlank(CharSequence...)
 *    - New: Compound check using StringUtils.isBlank() for each argument
 *    
 * 2. ClientHelper constructor signature change:
 *    - Old: ClientHelper(String, TaskListener, String, String)  
 *    - New: ClientHelper(Item, String, TaskListener, Workspace) or similar
 *    - Detection and guidance only (requires project-specific context)
 */
public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.err.println("\nThis tool identifies and fixes breaking changes from dependency updates:");
            System.err.println("1. StringUtils.isAllBlank() -> compound isBlank() checks");
            System.err.println("2. ClientHelper constructor signature changes (guidance only)");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        System.out.println("Scanning for breaking changes in: " + sourceDir);
        
        try {
            List<Path> javaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
            
            int filesProcessed = 0;
            int changesIdentified = 0;
            
            for (Path javaFile : javaFiles) {
                try {
                    CompilationUnit cu = new JavaParser().parse(javaFile)
                        .getResult()
                        .orElseThrow(() -> new IOException("Failed to parse: " + javaFile));
                    
                    BreakingChangeVisitor visitor = new BreakingChangeVisitor();
                    cu.accept(visitor, null);
                    
                    if (visitor.hasChanges()) {
                        System.out.println("\n" + javaFile + ":");
                        visitor.printFindings();
                        changesIdentified++;
                    }
                    
                    filesProcessed++;
                } catch (Exception e) {
                    System.err.println("Error processing " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("SUMMARY:");
            System.out.println("  Files processed: " + filesProcessed);
            System.out.println("  Files with breaking changes: " + changesIdentified);
            System.out.println("\nNEXT STEPS:");
            System.out.println("  1. For StringUtils.isAllBlank(): Replace with compound isBlank() checks");
            System.out.println("  2. For ClientHelper: Update constructor based on p4 plugin 1.11.5 API");
            System.out.println("=".repeat(60));
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
    
    static class BreakingChangeVisitor extends ModifierVisitor<Void> {
        private int stringUtilsChanges = 0;
        private int clientHelperChanges = 0;
        private Path currentFile;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Pattern: StringUtils.isAllBlank(arg1, arg2, ...)
            if (n.getNameAsString().equals("isAllBlank")) {
                boolean isStringUtilsCall = n.getScope().isPresent() && 
                    n.getScope().get().toString().equals("StringUtils");
                boolean isStaticImport = !n.getScope().isPresent();
                
                if (isStringUtilsCall || isStaticImport) {
                    stringUtilsChanges++;
                }
            }
            return super.visit(n, arg);
        }
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Pattern: new ClientHelper(String, TaskListener, String, String)
            if (n.getType().asString().equals("ClientHelper") && n.getArguments().size() == 4) {
                // Check if it looks like the old constructor pattern
                String arg2 = n.getArgument(1).toString().toLowerCase();
                if (arg2.contains("listener") || arg2.contains("tasklistener")) {
                    clientHelperChanges++;
                }
            }
            return super.visit(n, arg);
        }
        
        public boolean hasChanges() {
            return stringUtilsChanges > 0 || clientHelperChanges > 0;
        }
        
        public void printFindings() {
            if (stringUtilsChanges > 0) {
                System.out.println("  - Found " + stringUtilsChanges + " StringUtils.isAllBlank() calls");
                System.out.println("    FIX: Replace with compound isBlank() checks:");
                System.out.println("      OLD: StringUtils.isAllBlank(a, b, c)");
                System.out.println("      NEW: StringUtils.isBlank(a) && StringUtils.isBlank(b) && StringUtils.isBlank(c)");
            }
            
            if (clientHelperChanges > 0) {
                System.out.println("  - Found " + clientHelperChanges + " ClientHelper constructor calls");
                System.out.println("    FIX: Constructor signature changed in p4 plugin 1.11.5");
                System.out.println("      OLD: ClientHelper(String, TaskListener, String, String)");
                System.out.println("      NEW: ClientHelper(Item, String, TaskListener, Workspace)");
                System.out.println("      NEW: ClientHelper(ItemGroup, String, TaskListener, Workspace)");
                System.out.println("      NEW: ClientHelper(P4BaseCredentials, TaskListener, Workspace)");
                System.out.println("    You need Item/ItemGroup context and a Workspace object");
            }
        }
    }
}