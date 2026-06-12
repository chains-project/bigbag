package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java -jar transformation.jar <source-directory>");
            System.exit(1);
        }

        Path sourceDir = Paths.get(args[0]);
        Files.walkFileTree(sourceDir, new java.nio.file.SimpleFileVisitor<Path>() {
            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                if (file.toString().endsWith(".java")) {
                    try {
                        String content = Files.readString(file);
                        CompilationUnit cu = JavaParser.parse(content);
                        
                        // Transformation: Remove BytesOf and HexOf wrappers
                        cu.accept(new VoidVisitorAdapter<Void>() {
                            @Override
                            public void visit(final ObjectCreationExpr n, final Void arg) {
                                if (n.getType().toString().equals("org.cactoos.io.BytesOf")) {
                                    // Replace BytesOf(...) with its argument (the byte array)
                                    if (n.getArguments().size() == 1) {
                                        n.replace(n.getArguments().get(0));
                                    }
                                } else if (n.getType().toString().equals("org.cactoos.text.HexOf")) {
                                    // Replace HexOf(...) with a simple hex encoding approach
                                    if (n.getArguments().size() == 1) {
                                        // For now, we'll just remove the wrapper
                                        n.replace(n.getArguments().get(0));
                                    }
                                }
                                super.visit(n, arg);
                            }
                        }, null);
                        
                        // Write back the modified file
                        Files.write(file, cu.toString().getBytes());
                    } catch (Exception e) {
                        System.err.println("Error processing " + file + ": " + e.getMessage());
                    }
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
    }
}