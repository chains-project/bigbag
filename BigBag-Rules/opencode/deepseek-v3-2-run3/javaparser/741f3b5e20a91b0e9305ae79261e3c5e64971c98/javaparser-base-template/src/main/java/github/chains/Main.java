package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.Main <source-directory>");
            System.err.println("Transforms Java files to handle Jackson 2.13+ breaking changes:");
            System.err.println("1. Updates imports from JsonParseException to StreamReadException");
            System.err.println("2. Updates imports from JsonMappingException to DatabindException");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        try {
            transformProject(sourceDir);
            System.out.println("Transformation completed successfully.");
            System.out.println("NOTE: You may also need to update Maven dependencies:");
            System.out.println("1. Update jackson-core to version 2.13.x");
            System.out.println("2. Update jackson-annotations to version 2.13.x");
            System.out.println("3. Update jackson-datatype-* dependencies to version 2.13.x");
            System.out.println("4. Ensure all Jackson dependencies use compatible versions");
        } catch (IOException e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformProject(Path sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(sourceDir)
             .filter(p -> p.toString().endsWith(".java"))
             .forEach(javaFiles::add);
        
        JavaParser parser = new JavaParser();
        int modifiedCount = 0;
        
        for (Path javaFile : javaFiles) {
            Optional<CompilationUnit> cuOpt = parser.parse(javaFile).getResult();
            
            if (cuOpt.isPresent()) {
                CompilationUnit cu = cuOpt.get();
                boolean modified = transformCompilationUnit(cu);
                
                if (modified) {
                    DefaultPrettyPrinter printer = new DefaultPrettyPrinter(new DefaultPrinterConfiguration());
                    String transformedCode = printer.print(cu);
                    Files.write(javaFile, transformedCode.getBytes());
                    modifiedCount++;
                }
            }
        }
        
        System.out.println("Modified " + modifiedCount + " Java file(s)");
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        // Transform imports
        for (ImportDeclaration importDecl : cu.findAll(ImportDeclaration.class)) {
            String importName = importDecl.getNameAsString();
            
            // Update JsonParseException to StreamReadException
            if (importName.equals("com.fasterxml.jackson.core.JsonParseException")) {
                importDecl.setName("com.fasterxml.jackson.core.exc.StreamReadException");
                System.out.println("  Updated import: JsonParseException -> StreamReadException");
                modified = true;
            }
            // Update JsonMappingException to DatabindException
            else if (importName.equals("com.fasterxml.jackson.databind.JsonMappingException")) {
                importDecl.setName("com.fasterxml.jackson.databind.DatabindException");
                System.out.println("  Updated import: JsonMappingException -> DatabindException");
                modified = true;
            }
        }
        
        return modified;
    }
}