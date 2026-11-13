package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;
import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        transformDirectory(new File(sourceDir));
    }
    
    private static void transformDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                transformDirectory(file);
            } else if (file.getName().endsWith(".java")) {
                transformFile(file);
            }
        }
    }
    
    private static void transformFile(File file) {
        try {
            JavaParser parser = new JavaParser();
            Optional<CompilationUnit> cuOpt = parser.parse(file).getResult();
            
            if (!cuOpt.isPresent()) {
                System.err.println("Failed to parse: " + file);
                return;
            }
            
            CompilationUnit cu = cuOpt.get();
            boolean modified = false;
            
            // 1. Transform imports: replace TrustedTagInspector import with TagInspector import
            for (ImportDeclaration importDecl : cu.getImports()) {
                if (importDecl.getNameAsString().equals("org.yaml.snakeyaml.inspector.TrustedTagInspector")) {
                    importDecl.setName("org.yaml.snakeyaml.inspector.TagInspector");
                    modified = true;
                }
            }
            
            // 2. Transform ObjectCreationExpr: replace new TrustedTagInspector() with lambda
            // We assume TagInspector is a functional interface with method: boolean isTagAllowed(String tag)
            for (ObjectCreationExpr objectCreation : cu.findAll(ObjectCreationExpr.class)) {
                if (objectCreation.getTypeAsString().equals("TrustedTagInspector")) {
                    // Create lambda expression: tag -> true
                    // This assumes TagInspector has a single abstract method: boolean someMethod(String tag)
                    // The actual method name might need adjustment
                    try {
                        // Parse a lambda expression
                        JavaParser lambdaParser = new JavaParser();
                        LambdaExpr lambda = lambdaParser.parseExpression("tag -> true").getResult().get().asLambdaExpr();
                        
                        objectCreation.replace(lambda);
                        modified = true;
                    } catch (Exception e) {
                        System.err.println("Failed to create lambda for " + file + ": " + e.getMessage());
                    }
                }
            }
            
            if (modified) {
                PrinterConfiguration config = new DefaultPrinterConfiguration();
                DefaultPrettyPrinterVisitor visitor = new DefaultPrettyPrinterVisitor(config);
                cu.accept(visitor, null);
                String transformedCode = visitor.toString();
                
                Files.write(file.toPath(), transformedCode.getBytes());
                System.out.println("Transformed: " + file);
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + file + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
