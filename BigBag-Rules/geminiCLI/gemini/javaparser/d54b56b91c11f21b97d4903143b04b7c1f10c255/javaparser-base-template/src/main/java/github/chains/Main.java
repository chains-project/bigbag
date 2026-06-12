package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) throws IOException {
        Path projectDir = Paths.get("/workspace/java-pubsub-group-kafka-connector/src");

        try (Stream<Path> paths = Files.walk(projectDir)) {
            paths.filter(Files::isRegularFile)
                 .filter(p -> p.toString().endsWith(".java"))
                 .forEach(Main::processFile);
        }
    }

    private static void processFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            
            // 1. Replace imports
            cu.getImports().removeIf(importDecl -> {
                if ("com.google.cloud.pubsublite.PublishMetadata".equals(importDecl.getNameAsString())) {
                    return true; // We will add MessageMetadata later or rename it
                }
                if (importDecl.getNameAsString().startsWith("com.google.cloud.pubsublite.internal.wire.PubsubContext")) {
                    return true;
                }
                return false;
            });
            
            // Add MessageMetadata import if PublishMetadata was likely there or just add it if we find usages
            boolean needsMessageMetadata = false;

            // 2. Replace class usage
            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if ("PublishMetadata".equals(type.getNameAsString())) {
                    type.setName("MessageMetadata");
                    needsMessageMetadata = true;
                }
            }
            
            if (needsMessageMetadata) {
                cu.addImport("com.google.cloud.pubsublite.MessageMetadata");
            }

            // 3. Remove setContext calls
            cu.findAll(MethodCallExpr.class).forEach(method -> {
                if ("setContext".equals(method.getNameAsString())) {
                    if (method.getScope().isPresent()) {
                        method.replace(method.getScope().get());
                    }
                }
            });

            // 4. Remove FRAMEWORK field
            cu.findAll(FieldDeclaration.class).forEach(field -> {
                if (field.getVariables().stream().anyMatch(v -> v.getNameAsString().equals("FRAMEWORK"))) {
                    field.remove();
                }
            });

            Files.write(path, cu.toString().getBytes());
            System.out.println("Processed " + path);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}