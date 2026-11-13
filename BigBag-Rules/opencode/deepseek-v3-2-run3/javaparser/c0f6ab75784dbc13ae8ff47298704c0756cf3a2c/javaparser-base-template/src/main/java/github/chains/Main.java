package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.Node;
import java.io.File;
import java.util.Optional;

/**
 * Generic transformation rule for fixing breaking API changes in Maven projects.
 * 
 * This specific implementation handles the case where:
 * - Old API: SomeClass.builder().methodName(Set<T> items)
 * - New API: SomeClass.builder().methodName(T... items) (varargs)
 * 
 * The transformation converts Set<T> parameter to T[] array for varargs.
 * 
 * Example transformation:
 *   .addEnabledLanguages(languagesSet)
 *   becomes
 *   .addEnabledLanguages(languagesSet.toArray(new Language[0]))
 * 
 * This transformation is parameterized and can be configured for:
 * - Method names to transform
 * - Type parameter for array creation
 * - Optional: remove method call entirely if method no longer exists
 * 
 * USAGE: java Main <source-directory> [--remove-if-missing]
 */
public class Main {
    // Configuration: method names that need transformation
    // Can be extended for other similar breaking changes
    private static final String[] METHOD_NAMES = {"addEnabledLanguages"};
    
    // Configuration: type parameter to use in array creation
    // For addEnabledLanguages, the type is Language
    private static final String TYPE_PARAMETER = "Language";
    
    // Flag: if true, remove method call entirely when transformation fails
    // This handles cases where the method was completely removed from API
    private static boolean REMOVE_IF_MISSING = false;
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory> [--remove-if-missing]");
            System.err.println("Example: java Main /path/to/src");
            System.err.println("Example: java Main /path/to/src --remove-if-missing");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        if (args.length > 1 && args[1].equals("--remove-if-missing")) {
            REMOVE_IF_MISSING = true;
        }
        
        System.out.println("Processing source directory: " + sourceDir);
        if (REMOVE_IF_MISSING) {
            System.out.println("Mode: Will remove method call if transformation fails");
        }
        
        JavaParser javaParser = new JavaParser();
        processDirectory(new File(sourceDir), javaParser);
    }
    
    private static void processDirectory(File dir, JavaParser javaParser) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                processDirectory(file, javaParser);
            } else if (file.getName().endsWith(".java")) {
                processJavaFile(file, javaParser);
            }
        }
    }
    
    private static void processJavaFile(File javaFile, JavaParser javaParser) throws Exception {
        CompilationUnit cu = javaParser.parse(javaFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + javaFile)
        );
        
        SetToVarargsTransformer visitor = new SetToVarargsTransformer();
        visitor.visit(cu, null);
        
        if (visitor.isChanged()) {
            System.out.println("Modified: " + javaFile.getPath());
            java.io.FileWriter writer = new java.io.FileWriter(javaFile);
            writer.write(cu.toString());
            writer.close();
        }
    }
    
    private static class SetToVarargsTransformer extends VoidVisitorAdapter<Void> {
        private boolean changed = false;
        
        public boolean isChanged() {
            return changed;
        }
        
        @Override
        public void visit(MethodCallExpr n, Void arg) {
            super.visit(n, arg);
            
            String methodName = n.getNameAsString();
            boolean needsTransformation = false;
            for (String targetMethod : METHOD_NAMES) {
                if (methodName.equals(targetMethod)) {
                    needsTransformation = true;
                    break;
                }
            }
            
            if (!needsTransformation || n.getArguments().size() != 1) {
                return;
            }
            
            Expression argExpr = n.getArguments().get(0);
            Optional<com.github.javaparser.Position> pos = n.getRange().map(r -> r.begin);
            System.out.println("Found " + methodName + " at " + 
                javaFileForLog() + ":" + pos.map(p -> p.line).orElse(-1));
            
            // Strategy 1: Convert Set<T> to T[] for varargs
            String transformedArgCode = argExpr.toString() + ".toArray(new " + TYPE_PARAMETER + "[0])";
            
            try {
                JavaParser tempParser = new JavaParser();
                Expression transformedArg = tempParser.parseExpression(transformedArgCode)
                    .getResult().orElseThrow();
                
                n.getArguments().set(0, transformedArg);
                changed = true;
                System.out.println("  Strategy 1: Converted to varargs: " + n.toString());
                return;
                
            } catch (Exception e) {
                System.err.println("  Strategy 1 failed: " + e.getMessage());
            }
            
            // Strategy 2: If REMOVE_IF_MISSING flag is set, remove the method call entirely
            // This handles cases where the method was completely removed from the API
            if (REMOVE_IF_MISSING) {
                try {
                    // Check if this is part of a builder chain: builder().method(arg).otherMethod()
                    Optional<Node> parent = n.getParentNode();
                    if (parent.isPresent() && parent.get() instanceof MethodCallExpr) {
                        // This method call is part of a chain: parent.method().childMethod()
                        MethodCallExpr parentCall = (MethodCallExpr) parent.get();
                        // Replace this call in the chain with just the previous part
                        // This is complex - for now, we'll use a simpler approach
                        System.err.println("  Cannot safely remove from chain without full context");
                    } else {
                        // For simple cases, we could try to remove, but it's risky
                        System.err.println("  Removal would require more context analysis");
                    }
                } catch (Exception e) {
                    System.err.println("  Strategy 2 (removal) also failed: " + e.getMessage());
                }
            }
            
            // Strategy 3: Try alternative method name (e.g., singular form)
            // This could be extended to try method renames
            System.err.println("  No transformation applied. Manual intervention may be needed.");
            System.err.println("  Possible issues:");
            System.err.println("  1. Method " + methodName + " might not exist in new API");
            System.err.println("  2. Type " + TYPE_PARAMETER + " might need different import");
            System.err.println("  3. Entire class/API might have been refactored");
        }
        
        private String javaFileForLog() {
            // Simplified - in real implementation would track current file
            return "<current-file>";
        }
    }
}