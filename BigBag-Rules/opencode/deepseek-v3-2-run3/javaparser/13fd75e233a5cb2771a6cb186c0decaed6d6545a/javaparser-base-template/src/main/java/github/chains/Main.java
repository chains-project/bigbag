package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public class Main {
    // Configuration: Classes that changed their constructor signatures
    private static final Set<String> TARGET_CLASSES = new HashSet<>();
    
    static {
        // Hamcrest classes
        TARGET_CLASSES.add("StringContains");
        TARGET_CLASSES.add("StringStartsWith");
        // Add more classes here if needed
    }
    
    // Transformation mode: 
    // - "REMOVE_BOOLEAN": transforms Class(boolean, String) -> Class(String)
    // - "ADD_BOOLEAN": transforms Class(String) -> Class(boolean, String)
    private static final String TRANSFORMATION_MODE = "REMOVE_BOOLEAN";
    private static final boolean DEFAULT_BOOLEAN_VALUE = true;
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms constructor calls for Hamcrest classes");
            System.err.println("Target classes: " + String.join(", ", TARGET_CLASSES));
            System.err.println("Mode: " + TRANSFORMATION_MODE);
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Transforming constructor calls for Hamcrest classes...");
        System.out.println("Target classes: " + String.join(", ", TARGET_CLASSES));
        System.out.println("Mode: " + TRANSFORMATION_MODE);
        System.out.println("Source directory: " + sourceDir.toAbsolutePath());
        
        Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(Main::transformFile);
        
        System.out.println("Transformation completed.");
    }
    
    private static void transformFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(javaFile).getResult().orElseThrow();
            
            final boolean[] modified = new boolean[1];
            
            // Visitor to transform constructor calls
            VoidVisitorAdapter<Void> visitor = new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ObjectCreationExpr n, Void arg) {
                    super.visit(n, arg);
                    
                    // Check if this is a constructor call for one of our target classes
                    if (n.getType() instanceof ClassOrInterfaceType) {
                        ClassOrInterfaceType type = (ClassOrInterfaceType) n.getType();
                        String typeName = type.getNameAsString();
                        
                        // Check if it's one of the target classes
                        if (TARGET_CLASSES.contains(typeName)) {
                            if (TRANSFORMATION_MODE.equals("REMOVE_BOOLEAN")) {
                                // Transform Class(boolean, String) -> Class(String)
                                if (n.getArguments().size() == 2) {
                                    // Check if first argument is a boolean literal
                                    if (n.getArgument(0).isBooleanLiteralExpr()) {
                                        // Remove the first boolean argument
                                        NodeList<com.github.javaparser.ast.expr.Expression> newArgs = new NodeList<>();
                                        newArgs.add(n.getArgument(1));
                                        n.setArguments(newArgs);
                                        modified[0] = true;
                                    }
                                }
                            } else if (TRANSFORMATION_MODE.equals("ADD_BOOLEAN")) {
                                // Transform Class(String) -> Class(boolean, String)
                                if (n.getArguments().size() == 1) {
                                    // Add boolean as first argument
                                    NodeList<com.github.javaparser.ast.expr.Expression> newArgs = new NodeList<>();
                                    newArgs.add(new BooleanLiteralExpr(DEFAULT_BOOLEAN_VALUE));
                                    newArgs.addAll(n.getArguments());
                                    n.setArguments(newArgs);
                                    modified[0] = true;
                                }
                            }
                        }
                    }
                }
            };
            
            visitor.visit(cu, null);
            
            if (modified[0]) {
                System.out.println("Modified: " + javaFile);
                Files.write(javaFile, cu.toString().getBytes());
            }
        } catch (IOException e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
