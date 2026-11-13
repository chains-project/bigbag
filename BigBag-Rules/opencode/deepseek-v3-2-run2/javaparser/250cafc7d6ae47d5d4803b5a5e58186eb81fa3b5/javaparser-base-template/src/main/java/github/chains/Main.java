package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
    
    private static final String FLYWAY_CLASS = "org.flywaydb.core.Flyway";
    private static final String FLYWAY_CONFIGURER_CLASS = "org.flywaydb.core.api.configuration.FluentConfiguration";
    
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        try {
            List<File> javaFiles = findJavaFiles(sourceDir);
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int transformedFiles = 0;
            for (File javaFile : javaFiles) {
                if (transformFlywayUsage(javaFile)) {
                    transformedFiles++;
                }
            }
            
            System.out.println("Successfully transformed " + transformedFiles + " files");
        } catch (Exception e) {
            System.err.println("Error processing files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<File> findJavaFiles(String sourceDir) throws IOException {
        List<File> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> javaFiles.add(path.toFile()));
        return javaFiles;
    }
    
    private static boolean transformFlywayUsage(File javaFile) {
        try {
            JavaParser parser = new JavaParser();
            var parseResult = parser.parse(javaFile);
            
            if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
                CompilationUnit cu = parseResult.getResult().get();
                
                FlywayTransformer transformer = new FlywayTransformer();
                cu.accept(transformer, null);
                
                if (transformer.wasTransformed()) {
                    Files.write(javaFile.toPath(), cu.toString().getBytes());
                    System.out.println("Transformed: " + javaFile.getPath());
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Error transforming " + javaFile.getPath() + ": " + e.getMessage());
        }
        return false;
    }
    
    private static class FlywayTransformer extends ModifierVisitor<Void> {
        private boolean transformed = false;
        
        public boolean wasTransformed() {
            return transformed;
        }
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that return Flyway
            if (n.getType().toString().contains("Flyway")) {
                // This is a Flyway factory method - we need to transform the entire method body
                transformed = true;
                
                // Replace the entire method body with the new Flyway configuration pattern
                BlockStmt newBody = createNewFlywayMethodBody();
                n.setBody(newBody);
            }
            return super.visit(n, arg);
        }
        
        private BlockStmt new JavaParser().parseBlock("{\n" +
                "        final Properties prop = new Properties();\n" +
                "        prop.load(NisAppConfig.class.getClassLoader().getResourceAsStream(\"db.properties\"));\n" +
                "\n" +
                "        return Flyway.configure()\n" +
                "            .dataSource(this.dataSource())\n" +
                "            .classLoader(NisAppConfig.class.getClassLoader())\n" +
                "            .locations(prop.getProperty(\"flyway.locations\"))\n" +
                "            .validateOnMigrate(Boolean.valueOf(prop.getProperty(\"flyway.validate\")))\n" +
                "            .load();\n" +
                "    }").getResult().get();
        }
    }
}