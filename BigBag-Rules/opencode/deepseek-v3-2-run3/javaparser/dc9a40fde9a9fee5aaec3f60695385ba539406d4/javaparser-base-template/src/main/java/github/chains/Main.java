package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {
    
    public static class MethodSignatureChange {
        private final String className;
        private final String methodName;
        private final int oldParamCount;
        private final int newParamCount;
        private final String[] newParamDefaults;
        
        public MethodSignatureChange(String className, String methodName, 
                                     int oldParamCount, int newParamCount, 
                                     String[] newParamDefaults) {
            this.className = className;
            this.methodName = methodName;
            this.oldParamCount = oldParamCount;
            this.newParamCount = newParamCount;
            this.newParamDefaults = newParamDefaults;
        }
        
        public boolean matches(MethodCallExpr call) {
            try {
                // Check if method name matches
                if (!call.getNameAsString().equals(methodName)) {
                    return false;
                }
                
                // Check if we have the old parameter count
                if (call.getArguments().size() != oldParamCount) {
                    return false;
                }
                
                // Try to resolve the method to check class name
                // This requires type resolution which might not always work
                // For simplicity, we'll check based on the scope if present
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        public void transform(MethodCallExpr call) {
            // Add missing parameters with default values
            int missingParams = newParamCount - oldParamCount;
            for (int i = 0; i < missingParams; i++) {
                if (i < newParamDefaults.length) {
                    call.addArgument(newParamDefaults[i]);
                } else {
                    // Default fallback values based on common types
                    call.addArgument("null");
                }
            }
        }
    }
    
    public static class MethodSignatureTransformer extends VoidVisitorAdapter<Void> {
        private final List<MethodSignatureChange> transformations;
        private int transformationCount = 0;
        
        public MethodSignatureTransformer(List<MethodSignatureChange> transformations) {
            this.transformations = transformations;
        }
        
        @Override
        public void visit(MethodCallExpr call, Void arg) {
            super.visit(call, arg);
            
            for (MethodSignatureChange transformation : transformations) {
                if (transformation.matches(call)) {
                    transformation.transform(call);
                    transformationCount++;
                    break;
                }
            }
        }
        
        public int getTransformationCount() {
            return transformationCount;
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("Usage: java Main <sourceDir> <outputDir> <className> <methodName> <oldParamCount> <newParamCount> [defaultValues...]");
            System.err.println("Example: java Main /path/to/src /path/to/transformed org.apache.dubbo.rpc.support.RpcUtils getInvokeMode 2 3 false");
            System.err.println("Example for multiple transformations: java Main /path/to/src /path/to/transformed config.json");
            System.err.println("Where config.json contains JSON array of transformations");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        List<MethodSignatureChange> transformations = new ArrayList<>();
        
        if (args.length == 3 && args[2].endsWith(".json")) {
            // Load transformations from JSON config file
            transformations = loadTransformationsFromConfig(args[2]);
        } else {
            // Parse transformation from command line arguments
            String className = args[2];
            String methodName = args[3];
            int oldParamCount = Integer.parseInt(args[4]);
            int newParamCount = Integer.parseInt(args[5]);
            
            String[] defaultValues = new String[0];
            if (args.length > 6) {
                defaultValues = Arrays.copyOfRange(args, 6, args.length);
            }
            
            transformations.add(new MethodSignatureChange(
                className,
                methodName,
                oldParamCount,
                newParamCount,
                defaultValues
            ));
        }
        
        // Process all Java files in source directory
        try {
            processDirectory(Paths.get(sourceDir), Paths.get(outputDir), transformations);
            System.out.println("Transformation completed successfully");
            System.out.println("Applied " + transformations.size() + " transformation rule(s)");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<MethodSignatureChange> loadTransformationsFromConfig(String configPath) {
        // Simplified implementation - in real scenario would parse JSON
        List<MethodSignatureChange> transformations = new ArrayList<>();
        // For now, return empty list
        return transformations;
    }
    
    private static void processDirectory(Path sourceDir, Path outputDir, 
                                         List<MethodSignatureChange> transformations) 
            throws Exception {
        
        // Setup JavaParser with symbol solver
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(symbolSolver);
        
        File sourceFile = sourceDir.toFile();
        if (!sourceFile.exists()) {
            throw new FileNotFoundException("Source directory not found: " + sourceDir);
        }
        
        if (sourceFile.isDirectory()) {
            // Recursively process all .java files
            processJavaFiles(sourceFile, outputDir.toFile(), parser, transformations);
        } else if (sourceFile.getName().endsWith(".java")) {
            // Process single file
            processJavaFile(sourceFile, outputDir.toFile(), parser, transformations);
        }
    }
    
    private static void processJavaFiles(File sourceDir, File outputDir, 
                                         JavaParser parser, 
                                         List<MethodSignatureChange> transformations) 
            throws Exception {
        
        File[] files = sourceDir.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                processJavaFiles(file, new File(outputDir, file.getName()), parser, transformations);
            } else if (file.getName().endsWith(".java")) {
                File outputFile = new File(outputDir, file.getName());
                processJavaFile(file, outputFile, parser, transformations);
            }
        }
    }
    
    private static void processJavaFile(File sourceFile, File outputFile, 
                                        JavaParser parser, 
                                        List<MethodSignatureChange> transformations) 
            throws Exception {
        
        // Create output directory if it doesn't exist
        outputFile.getParentFile().mkdirs();
        
        // Parse the Java file
        CompilationUnit cu = parser.parse(sourceFile).getResult().orElseThrow(
            () -> new RuntimeException("Failed to parse " + sourceFile)
        );
        
        // Apply transformations
        MethodSignatureTransformer transformer = new MethodSignatureTransformer(transformations);
        transformer.visit(cu, null);
        
        // Write transformed file
        try {
            java.nio.file.Files.write(
                outputFile.toPath(),
                cu.toString().getBytes()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to write file: " + outputFile, e);
        }
        
        System.out.println("Processed: " + sourceFile + " (" + transformer.getTransformationCount() + " transformations)");
    }
}