package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java -jar javaparser.jar <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }

        List<File> javaFiles = Files.walk(sourceDir)
            .filter(p -> p.toString().endsWith(".java"))
            .map(Path::toFile)
            .collect(Collectors.toList());

        System.out.println("Found " + javaFiles.size() + " Java files to process");

        JavaParser parser = new JavaParser();
        int modifiedFiles = 0;

        for (File file : javaFiles) {
            try (FileInputStream in = new FileInputStream(file)) {
                CompilationUnit cu = parser.parse(in).getResult().orElse(null);
                if (cu == null) {
                    continue;
                }

                boolean[] wasModified = {false};
                
                cu.accept(new ModifierVisitor<Void>() {
                    @Override
                    public Visitable visit(MethodCallExpr n, Void arg) {
                        // Check if this is JAXBToStringStrategy.getInstance()
                        if (n.getNameAsString().equals("getInstance")) {
                            if (n.getScope().isPresent()) {
                                String scope = n.getScope().get().toString();
                                // Check for both simple and fully qualified name
                                if (scope.endsWith("JAXBToStringStrategy")) {
                                    // Replace with JAXBToStringStrategy.INSTANCE
                                    FieldAccessExpr replacement = new FieldAccessExpr(
                                        n.getScope().get().clone(), 
                                        "INSTANCE"
                                    );
                                    wasModified[0] = true;
                                    return replacement;
                                }
                            }
                        }
                        return super.visit(n, arg);
                    }
                }, null);

                if (wasModified[0]) {
                    // Write back the modified file
                    Files.write(file.toPath(), cu.toString().getBytes());
                    modifiedFiles++;
                    System.out.println("Modified: " + file.getPath());
                }
            } catch (Exception e) {
                System.err.println("Error processing file: " + file.getPath());
                e.printStackTrace();
            }
        }

        System.out.println("\nTransformation complete. Modified " + modifiedFiles + " files.");
        System.out.println("\nSummary of transformation:");
        System.out.println("Old API pattern: JAXBToStringStrategy.getInstance()");
        System.out.println("New API pattern: JAXBToStringStrategy.INSTANCE");
        System.out.println("\nThis transformation fixes the breaking change in jaxb2-basics-runtime 1.11.1+");
    }
}