package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic JavaParser transformation for API migration.
 * 
 * This transformation handles the breaking change where:
 * - Old: CoverageDatabase.getClassInfo(Set<ClassName>) returns Collection<ClassInfo>
 * - New: Method removed from CoverageDatabase, available on CodeSource.getClassInfo(Collection<ClassName>)
 * 
 * The transformation:
 * 1. Finds calls to getClassInfo() method
 * 2. Replaces the receiver with a configurable alternative (default: "codeSource")
 * 3. Adds import for CodeSource if needed
 * 4. Provides configurable parameters for different migration scenarios
 */
public class Main {
    
    // Configuration - can be made configurable via command line or properties
    private static class Config {
        // Old API configuration
        String oldType = "org.pitest.coverage.CoverageDatabase";
        String oldMethod = "getClassInfo";
        
        // New API configuration  
        String newType = "org.pitest.classpath.CodeSource";
        String newMethod = "getClassInfo";
        String newReceiver = "codeSource"; // Variable name for new receiver
        
        // Transformation behavior
        boolean addImport = true;
        boolean addComment = true;
    }
    
    private final Config config;
    
    public Main(Config config) {
        this.config = config;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        // Parse optional arguments
        Config config = new Config();
        for (int i = 2; i < args.length; i++) {
            if (args[i].equals("--receiver") && i + 1 < args.length) {
                config.newReceiver = args[++i];
            } else if (args[i].equals("--old-type") && i + 1 < args.length) {
                config.oldType = args[++i];
            } else if (args[i].equals("--new-type") && i + 1 < args.length) {
                config.newType = args[++i];
            } else if (args[i].equals("--no-import")) {
                config.addImport = false;
            } else if (args[i].equals("--no-comment")) {
                config.addComment = false;
            }
        }
        
        Main transformer = new Main(config);
        transformer.transformDirectory(sourceDir, outputDir);
    }
    
    private static void printUsage() {
        System.err.println("Generic API Migration Transformation Tool");
        System.err.println("Usage: java -jar javaparser.jar <source-dir> <output-dir> [options]");
        System.err.println();
        System.err.println("Required:");
        System.err.println("  <source-dir>    Directory containing Java source files");
        System.err.println("  <output-dir>    Directory to write transformed files");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  --receiver <name>    Variable name for new receiver (default: codeSource)");
        System.err.println("  --old-type <type>    Fully qualified old type (default: org.pitest.coverage.CoverageDatabase)");
        System.err.println("  --new-type <type>    Fully qualified new type (default: org.pitest.classpath.CodeSource)");
        System.err.println("  --no-import         Don't add import for new type");
        System.err.println("  --no-comment        Don't add transformation comments");
        System.err.println();
        System.err.println("Example (PIT 1.10.0 migration):");
        System.err.println("  java -jar javaparser.jar src/ out/ --receiver codeSource");
        System.err.println();
        System.err.println("Transformation:");
        System.err.println("  Finds: <receiver>.getClassInfo(<args>)");
        System.err.println("  Replaces with: codeSource.getClassInfo(<args>)");
        System.err.println("  Assumes 'codeSource' variable is available or will be injected");
    }
    
    public void transformDirectory(String sourceDir, String outputDir) throws Exception {
        System.out.println("=== Generic API Migration Transformation ===");
        System.out.println("Source directory: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Old API: " + config.oldType + "." + config.oldMethod + "()");
        System.out.println("  New API: " + config.newType + "." + config.newMethod + "()");
        System.out.println("  New receiver variable: " + config.newReceiver);
        System.out.println();
        
        JavaParser javaParser = new JavaParser();
        List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
        System.out.println("Found " + javaFiles.size() + " Java file(s)");
        
        int transformedFiles = 0;
        int transformedCalls = 0;
        List<String> issues = new ArrayList<>();
        
        for (Path javaFile : javaFiles) {
            try {
                TransformationResult result = transformFile(javaParser, javaFile, Paths.get(outputDir));
                if (result.transformedCalls > 0) {
                    transformedFiles++;
                    transformedCalls += result.transformedCalls;
                    System.out.println("✓ " + javaFile + ": " + result.transformedCalls + " call(s) transformed");
                }
            } catch (Exception e) {
                String error = "✗ " + javaFile + ": " + e.getMessage();
                System.err.println(error);
                issues.add(error);
            }
        }
        
        System.out.println("\n=== Summary ===");
        System.out.println("Files processed: " + javaFiles.size());
        System.out.println("Files transformed: " + transformedFiles);
        System.out.println("Method calls transformed: " + transformedCalls);
        
        if (!issues.isEmpty()) {
            System.out.println("\n=== Issues ===");
            issues.forEach(System.out::println);
        }
        
        System.out.println("\n=== Next Steps ===");
        System.out.println("1. Review transformed code in " + outputDir);
        System.out.println("2. Ensure '" + config.newReceiver + "' variable of type " + config.newType + " is available");
        System.out.println("3. Update constructors/methods to accept " + config.newType + " parameter if needed");
        System.out.println("4. Compile and test the migrated code");
        System.out.println("\nNote: This is a structural transformation. Manual adjustments may be needed");
        System.out.println("      for proper dependency injection and type resolution.");
    }
    
    private TransformationResult transformFile(JavaParser javaParser, Path inputFile, Path outputDir) throws Exception {
        String content = Files.readString(inputFile);
        CompilationUnit cu = javaParser.parse(content).getResult()
            .orElseThrow(() -> new RuntimeException("Failed to parse " + inputFile));
        
        TransformerVisitor visitor = new TransformerVisitor();
        cu.accept(visitor, null);
        
        if (visitor.transformedCalls > 0 && config.addImport) {
            // Add import for new type if not already present
            boolean hasImport = cu.getImports().stream()
                .anyMatch(imp -> imp.getNameAsString().equals(config.newType));
            if (!hasImport) {
                cu.addImport(config.newType);
            }
        }
        
        // Write output file
        Path outputPath = outputDir.resolve(inputFile.getFileName());
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, cu.toString());
        
        return new TransformationResult(visitor.transformedCalls, outputPath);
    }
    
    private List<Path> findJavaFiles(Path dir) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        if (Files.exists(dir) && Files.isDirectory(dir)) {
            Files.walk(dir)
                 .filter(path -> path.toString().endsWith(".java") && Files.isRegularFile(path))
                 .forEach(javaFiles::add);
        }
        return javaFiles;
    }
    
    private class TransformerVisitor extends ModifierVisitor<Void> {
        int transformedCalls = 0;
        
        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Check if this is the method we're looking for
            if (config.oldMethod.equals(n.getNameAsString())) {
                // Transform the method call
                transformedCalls++;
                
                // Create new method call with new receiver
                NodeList<Expression> args = n.getArguments();
                MethodCallExpr newCall = new MethodCallExpr(
                    new NameExpr(config.newReceiver),
                    config.newMethod,
                    args
                );
                
                if (config.addComment) {
                    newCall.setLineComment(" API migration: " + config.oldType + "." + 
                                          config.oldMethod + "() -> " + config.newType + "." + 
                                          config.newMethod + "()");
                }
                
                return newCall;
            }
            
            return super.visit(n, arg);
        }
    }
    
    private static class TransformationResult {
        final int transformedCalls;
        final Path outputPath;
        
        TransformationResult(int transformedCalls, Path outputPath) {
            this.transformedCalls = transformedCalls;
            this.outputPath = outputPath;
        }
    }
}