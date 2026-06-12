package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    
    // Configuration for method transformations
    // Key: method name, Value: map of old arg count to new arg expressions
    private static final Map<String, Map<Integer, List<String>>> METHOD_TRANSFORMATIONS = new HashMap<>();
    
    static {
        // Example: BufferedSink.write(ByteString) -> BufferedSink.write(ByteString, offset, byteCount)
        Map<Integer, List<String>> writeTransformations = new HashMap<>();
        List<String> newArgs = new ArrayList<>();
        newArgs.add("0");  // offset
        newArgs.add("byteString.size()");  // byteCount
        writeTransformations.put(1, newArgs);
        METHOD_TRANSFORMATIONS.put("write", writeTransformations);
        
        // Example: BufferedSink.writeUtf8(String) -> BufferedSink.writeUtf8(String, charset)
        Map<Integer, List<String>> writeUtf8Transformations = new HashMap<>();
        List<String> newUtf8Args = new ArrayList<>();
        newUtf8Args.add("java.nio.charset.StandardCharsets.UTF_8");
        writeUtf8Transformations.put(1, newUtf8Args);
        METHOD_TRANSFORMATIONS.put("writeUtf8", writeUtf8Transformations);
        
        // Example: Method that removes a parameter
        // Map<Integer, List<String>> removeParamTransformations = new HashMap<>();
        // removeParamTransformations.put(2, new ArrayList<>()); // Remove all args (or specific ones)
        // METHOD_TRANSFORMATIONS.put("oldMethod", removeParamTransformations);
    }
    
    public static class ApiTransformationVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            MethodCallExpr methodCall = (MethodCallExpr) super.visit(n, arg);
            
            String methodName = methodCall.getNameAsString();
            int argumentCount = methodCall.getArguments().size();
            
            // Check if we have a transformation for this method with the current argument count
            if (METHOD_TRANSFORMATIONS.containsKey(methodName)) {
                Map<Integer, List<String>> transformations = METHOD_TRANSFORMATIONS.get(methodName);
                
                if (transformations.containsKey(argumentCount)) {
                    List<String> newArgs = transformations.get(argumentCount);
                    
                    // For a real implementation, we would check argument types
                    // Here we just apply the transformation
                    for (String argExpr : newArgs) {
                        methodCall.addArgument(argExpr);
                    }
                    
                    // Alternatively, we could replace arguments
                    // methodCall.getArguments().clear();
                    // for (String argExpr : newArgs) {
                    //     methodCall.addArgument(new NameExpr(argExpr));
                    // }
                }
            }
            
            return methodCall;
        }
    }
    
    public static void transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(filePath).getResult().orElseThrow();
        
        ApiTransformationVisitor visitor = new ApiTransformationVisitor();
        visitor.visit(cu, null);
        
        // Save the transformed file
        PrinterConfiguration config = new DefaultPrinterConfiguration();
        String transformedCode = cu.toString(config);
        Files.write(filePath, transformedCode.getBytes());
    }
    
    public static List<Path> findJavaFiles(Path rootDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(rootDir)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Example: java github.chains.Main /path/to/project/src");
            System.err.println("\nThis tool applies generic API transformation rules to fix breaking changes.");
            System.err.println("Currently configured transformations:");
            for (Map.Entry<String, Map<Integer, List<String>>> entry : METHOD_TRANSFORMATIONS.entrySet()) {
                System.err.println("  " + entry.getKey() + ": " + entry.getValue());
            }
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            System.out.println("Scanning for Java files in: " + sourceDir);
            List<Path> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            System.out.println("Applying transformations: " + METHOD_TRANSFORMATIONS.keySet());
            
            int transformedCount = 0;
            for (Path javaFile : javaFiles) {
                System.out.println("Processing: " + javaFile);
                try {
                    // Backup original file
                    Path backupFile = javaFile.resolveSibling(javaFile.getFileName() + ".bak");
                    if (!Files.exists(backupFile)) {
                        Files.copy(javaFile, backupFile);
                    }
                    
                    transformFile(javaFile);
                    transformedCount++;
                    
                    System.out.println("  Transformed: " + javaFile);
                } catch (Exception e) {
                    System.err.println("  Error processing " + javaFile + ": " + e.getMessage());
                }
            }
            
            System.out.println("\nTransformation complete.");
            System.out.println("Successfully transformed " + transformedCount + " out of " + javaFiles.size() + " files");
            
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}