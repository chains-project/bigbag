package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    // Configuration parameters - these should be parameterized for different projects
    private static final String OLD_STRINGUTILS_IMPORT = "liquibase.util.StringUtils";
    private static final String NEW_STRINGUTILS_IMPORT = "liquibase.repackaged.org.apache.commons.lang3.StringUtils";
    private static final String EXECUTOR_SERVICE_CLASS = "ExecutorService";
    private static final String SCOPE_CLASS = "Scope";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Transforming source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findAllJavaFiles(new File(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            int stringUtilsTransforms = 0;
            int executorServiceTransforms = 0;
            
            for (File javaFile : javaFiles) {
                final boolean[] fileModified = {false};
                final int[] localExecutorServiceTransforms = {0};
                
                // Parse the Java file
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow(
                    () -> new RuntimeException("Failed to parse " + javaFile)
                );
                
                // Transform StringUtils imports
                List<ImportDeclaration> imports = cu.getImports();
                for (ImportDeclaration importDecl : imports) {
                    if (importDecl.getNameAsString().equals(OLD_STRINGUTILS_IMPORT)) {
                        importDecl.setName(NEW_STRINGUTILS_IMPORT);
                        stringUtilsTransforms++;
                        fileModified[0] = true;
                    }
                }
                
// Transform ExecutorService.getInstance() calls
                cu.accept(new VoidVisitorAdapter<Void>() {
                    @Override
                    public void visit(MethodCallExpr n, Void arg) {
                        super.visit(n, arg);
                        
                        // Check for pattern: ExecutorService.getInstance()
                        if (n.getNameAsString().equals("getInstance")) {
                            if (n.getScope().isPresent()) {
                                var scope = n.getScope().get();
                                boolean shouldTransform = false;
                                
                                if (scope instanceof NameExpr) {
                                    NameExpr nameExpr = (NameExpr) scope;
                                    if (nameExpr.getNameAsString().equals(EXECUTOR_SERVICE_CLASS)) {
                                        shouldTransform = true;
                                    }
                                } else if (scope instanceof FieldAccessExpr) {
                                    FieldAccessExpr fa = (FieldAccessExpr) scope;
                                    // Check if it ends with .ExecutorService
                                    if (fa.getNameAsString().equals(EXECUTOR_SERVICE_CLASS)) {
                                        shouldTransform = true;
                                    }
                                }
                                
                                if (shouldTransform) {
                                    // Replace with Scope.getCurrentScope().getSingleton(ExecutorService.class)
                                    MethodCallExpr getCurrentScope = new MethodCallExpr(
                                        new NameExpr(SCOPE_CLASS), 
                                        "getCurrentScope"
                                    );
                                    
                                    NodeList<com.github.javaparser.ast.expr.Expression> argsList = new NodeList<>();
                                    argsList.add(new ClassExpr(new ClassOrInterfaceType(null, EXECUTOR_SERVICE_CLASS)));
                                    
                                    MethodCallExpr getSingletonCall = new MethodCallExpr(
                                        getCurrentScope,
                                        "getSingleton",
                                        argsList
                                    );
                                    
                                    n.replace(getSingletonCall);
                                    executorServiceTransforms++;
                                    fileModified[0] = true;
                                    
                                    // Check if Scope import is needed
                                    boolean hasScopeImport = false;
                                    for (ImportDeclaration importDecl : cu.getImports()) {
                                        if (importDecl.getNameAsString().equals("liquibase.Scope")) {
                                            hasScopeImport = true;
                                            break;
                                        }
                                    }
                                    if (!hasScopeImport) {
                                        cu.addImport("liquibase.Scope");
                                    }
                                }
                            }
                        }
                    }
                }, null);
                
                // Write back if modified
                if (fileModified[0]) {
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    transformedFiles++;
                    executorServiceTransforms += localExecutorServiceTransforms[0];
                    System.out.println("Transformed: " + javaFile.getPath());
                }
            }
            
            System.out.println("\nTransformation Summary:");
            System.out.println("  Total files processed: " + javaFiles.size());
            System.out.println("  Files transformed: " + transformedFiles);
            System.out.println("  StringUtils import updates: " + stringUtilsTransforms);
            System.out.println("  ExecutorService.getInstance() replacements: " + executorServiceTransforms);
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findAllJavaFiles(File directory) {
        List<File> javaFiles = new ArrayList<>();
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    javaFiles.addAll(findAllJavaFiles(file));
                } else if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }
        return javaFiles;
    }
}
