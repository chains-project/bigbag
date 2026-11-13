package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be configurable for different projects
    // These would typically be passed as command-line arguments or configuration file
    private static final String OLD_TYPE_SIMPLE = "Date";
    private static final String NEW_TYPE_SIMPLE = "Timestamp";
    private static final String OLD_TYPE_FULL = "java.util.Date";
    private static final String NEW_TYPE_FULL = "java.sql.Timestamp";
    
    // Methods that changed signature from Date to Timestamp in net.datafaker.DateAndTime
    // This list should be configurable based on the specific breaking change
    private static final List<String> AFFECTED_METHODS = List.of(
        "between",
        "birthday"
    );
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("This transformation fixes breaking changes from " + OLD_TYPE_FULL + " to " + NEW_TYPE_FULL);
            System.err.println("Affected methods: " + AFFECTED_METHODS);
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming Java files in: " + sourceDir);
        System.out.println("Fixing breaking API change: " + OLD_TYPE_FULL + " -> " + NEW_TYPE_FULL);
        System.out.println("For methods: " + AFFECTED_METHODS);
        
        try {
            transformDirectory(new File(sourceDir));
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformDirectory(File directory) throws FileNotFoundException {
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("Invalid directory: " + directory.getPath());
        }
        
        List<File> javaFiles = findJavaFiles(directory);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int transformedFiles = 0;
        int transformedMethods = 0;
        
        for (File javaFile : javaFiles) {
            TransformResult result = transformFile(javaFile);
            if (result.fileChanged) {
                transformedFiles++;
            }
            transformedMethods += result.methodsTransformed;
        }
        
        System.out.println("Transformed " + transformedMethods + " methods in " + transformedFiles + " files");
    }
    
    private static List<File> findJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        findJavaFilesRecursive(directory, javaFiles);
        return javaFiles;
    }
    
    private static void findJavaFilesRecursive(File directory, List<File> javaFiles) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                findJavaFilesRecursive(file, javaFiles);
            } else if (file.getName().endsWith(".java")) {
                javaFiles.add(file);
            }
        }
    }
    
    private static TransformResult transformFile(File javaFile) throws FileNotFoundException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
        
        DateToTimestampVisitor visitor = new DateToTimestampVisitor();
        visitor.visit(cu, null);
        
        if (visitor.wasChanged()) {
            // Write the transformed file
            try (PrintWriter writer = new PrintWriter(javaFile)) {
                writer.write(cu.toString());
            }
            return new TransformResult(true, visitor.getMethodsTransformed());
        }
        
        return new TransformResult(false, 0);
    }
    
    private static class TransformResult {
        final boolean fileChanged;
        final int methodsTransformed;
        
        TransformResult(boolean fileChanged, int methodsTransformed) {
            this.fileChanged = fileChanged;
            this.methodsTransformed = methodsTransformed;
        }
    }
    
    private static class DateToTimestampVisitor extends VoidVisitorAdapter<Void> {
        private boolean changed = false;
        private int methodsTransformed = 0;
        
        public boolean wasChanged() {
            return changed;
        }
        
        public int getMethodsTransformed() {
            return methodsTransformed;
        }
        
        @Override
        public void visit(MethodDeclaration md, Void arg) {
            super.visit(md, arg);
            
            // Check if method name matches affected methods
            String methodName = md.getNameAsString();
            if (!AFFECTED_METHODS.contains(methodName)) {
                return;
            }
            
            // Check if method parameters use java.util.Date
            // Note: getType().toString() returns simple name (e.g., "Date") not full name
            boolean hasDateParams = md.getParameters().stream()
                .anyMatch(param -> param.getType().toString().equals(OLD_TYPE_SIMPLE));
            
            // Check if return type is java.util.Date
            boolean hasDateReturn = md.getType().toString().equals(OLD_TYPE_SIMPLE);
            
            if (hasDateParams || hasDateReturn) {
                System.out.println("Transforming method: " + methodName + " in " + 
                    md.findCompilationUnit().get().getStorage().get().getPath());
                
                methodsTransformed++;
                
                // Transform return type from Date to Timestamp
                if (hasDateReturn) {
                    md.setType(NEW_TYPE_SIMPLE);
                    changed = true;
                }
                
                // Transform parameters from Date to Timestamp
                md.getParameters().forEach(param -> {
                    if (param.getType().toString().equals(OLD_TYPE_SIMPLE)) {
                        param.setType(NEW_TYPE_SIMPLE);
                        changed = true;
                    }
                });
                
                // Note: We keep @Override annotation since after transformation,
                // the method should correctly override the parent class method
                // with the new signature (Timestamp instead of Date)
                
                // Transform method body: if calling super.method() with Date arguments,
                // we need to convert them to Timestamp
                transformMethodBody(md);
            }
        }
        
        private void transformMethodBody(MethodDeclaration md) {
            md.getBody().ifPresent(body -> {
                // Find all super.method() calls
                body.findAll(MethodCallExpr.class).forEach(mce -> {
                    if (mce.getScope().isPresent() && mce.getScope().get().toString().equals("super")) {
                        String methodName = mce.getNameAsString();
                        if (AFFECTED_METHODS.contains(methodName)) {
                            // Transform arguments: if they are Date objects being passed to a method
                            // that now expects Timestamp, we need to convert them
                            // Note: In the actual case, if we've changed the method parameters
                            // to Timestamp, then the arguments are already Timestamp objects,
                            // so no conversion is needed in the method body
                        }
                    }
                });
            });
        }
    }
}