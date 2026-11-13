package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic JavaParser transformation rule for removing deprecated classes
 * and replacing their static field access with literal values.
 * 
 * This transformation handles the breaking change where a class with
 * numeric constants (like com.jcabi.aspects.Tv) is removed from a dependency.
 * 
 * Configuration: Modify CLASS_TO_REMOVE and CONSTANT_MAPPING for your specific use case.
 */
public class Main {
    // Configuration: Set these for your specific transformation
    private static final String CLASS_TO_REMOVE = "com.jcabi.aspects.Tv";
    private static final String CLASS_SIMPLE_NAME = "Tv";
    
    // Mapping of constant names to their replacement values
    private static final Map<String, String> CONSTANT_MAPPING = new HashMap<>();
    
    static {
        // Configure the constant mapping for your specific case
        // Example: Tv class constants mapping
        CONSTANT_MAPPING.put("ONE", "1");
        CONSTANT_MAPPING.put("TWO", "2");
        CONSTANT_MAPPING.put("THREE", "3");
        CONSTANT_MAPPING.put("FOUR", "4");
        CONSTANT_MAPPING.put("FIVE", "5");
        CONSTANT_MAPPING.put("SIX", "6");
        CONSTANT_MAPPING.put("SEVEN", "7");
        CONSTANT_MAPPING.put("EIGHT", "8");
        CONSTANT_MAPPING.put("NINE", "9");
        CONSTANT_MAPPING.put("TEN", "10");
        CONSTANT_MAPPING.put("ELEVEN", "11");
        CONSTANT_MAPPING.put("TWELVE", "12");
        CONSTANT_MAPPING.put("THIRTEEN", "13");
        CONSTANT_MAPPING.put("FOURTEEN", "14");
        CONSTANT_MAPPING.put("FIFTEEN", "15");
        CONSTANT_MAPPING.put("SIXTEEN", "16");
        CONSTANT_MAPPING.put("SEVENTEEN", "17");
        CONSTANT_MAPPING.put("EIGHTEEN", "18");
        CONSTANT_MAPPING.put("NINETEEN", "19");
        CONSTANT_MAPPING.put("TWENTY", "20");
        CONSTANT_MAPPING.put("THIRTY", "30");
        CONSTANT_MAPPING.put("FORTY", "40");
        CONSTANT_MAPPING.put("FIFTY", "50");
        CONSTANT_MAPPING.put("SIXTY", "60");
        CONSTANT_MAPPING.put("SEVENTY", "70");
        CONSTANT_MAPPING.put("EIGHTY", "80");
        CONSTANT_MAPPING.put("NINETY", "90");
        CONSTANT_MAPPING.put("HUNDRED", "100");
        CONSTANT_MAPPING.put("THOUSAND", "1000");
        CONSTANT_MAPPING.put("MILLION", "1000000");
        CONSTANT_MAPPING.put("BILLION", "1000000000");
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.err.println("Example: java -jar javaparser.jar /path/to/project/src");
            System.err.println("");
            System.err.println("Transforming: " + CLASS_TO_REMOVE);
            System.err.println("Constants mapping: " + CONSTANT_MAPPING.size() + " constants defined");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Removing class: " + CLASS_TO_REMOVE);
        
        try {
            Path sourcePath = Paths.get(sourceDir);
            if (Files.isDirectory(sourcePath)) {
                Files.walk(sourcePath)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::processJavaFile);
            } else if (Files.isRegularFile(sourcePath) && sourcePath.toString().endsWith(".java")) {
                processJavaFile(sourcePath);
            } else {
                System.err.println("Error: Source path must be a directory or a .java file");
                System.exit(1);
            }
            
            System.out.println("Transformation completed successfully.");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void processJavaFile(Path javaFile) {
        try {
            JavaParser parser = new JavaParser();
            CompilationUnit cu = parser.parse(new FileInputStream(javaFile.toFile())).getResult().orElseThrow(() -> 
                new RuntimeException("Failed to parse " + javaFile));
            
            // Use an array to work around the effectively final requirement
            final boolean[] fileModified = {false};
            
            // Collect imports to remove
            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(ImportDeclaration decl, Void arg) {
                    super.visit(decl, arg);
                    if (decl.getNameAsString().equals(CLASS_TO_REMOVE)) {
                        importsToRemove.add(decl);
                        fileModified[0] = true;
                    }
                }
            }, null);
            
            // Remove collected imports
            importsToRemove.forEach(ImportDeclaration::remove);
            
            // Collect constant references to replace
            List<FieldAccessExpr> constantsToReplace = new ArrayList<>();
            cu.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(FieldAccessExpr expr, Void arg) {
                    super.visit(expr, arg);
                    
                    // Check if this is a ClassName.CONSTANT expression
                    if (expr.getScope().isNameExpr()) {
                        NameExpr scope = expr.getScope().asNameExpr();
                        if (scope.getNameAsString().equals(CLASS_SIMPLE_NAME)) {
                            constantsToReplace.add(expr);
                            fileModified[0] = true;
                        }
                    }
                }
            }, null);
            
            // Replace collected constants with literal values
            for (FieldAccessExpr expr : constantsToReplace) {
                String constantName = expr.getNameAsString();
                String replacementValue = CONSTANT_MAPPING.get(constantName);
                
                if (replacementValue != null) {
                    // Replace ClassName.CONSTANT with literal value
                    IntegerLiteralExpr replacement = new IntegerLiteralExpr(replacementValue);
                    expr.replace(replacement);
                    System.out.println("Replaced " + CLASS_SIMPLE_NAME + "." + constantName + 
                                       " with " + replacementValue + " in " + javaFile);
                } else {
                    System.err.println("Warning: Unknown constant '" + CLASS_SIMPLE_NAME + 
                                       "." + constantName + "' in " + javaFile + 
                                       " (not in mapping)");
                }
            }
            
            // Write the transformed file back only if modifications were made
            if (fileModified[0]) {
                Files.write(javaFile, cu.toString().getBytes());
            }
            
        } catch (Exception e) {
            System.err.println("Error processing file " + javaFile + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}