package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String HTTP_SESSION = "jakarta.servlet.http.HttpSession";
    private static final String HTTP_SESSION_CONTEXT = "jakarta.servlet.http.HttpSessionContext";
    private static final Set<String> REMOVED_HTTP_SESSION_METHODS = Set.of(
            "getSessionContext",
            "getValue",
            "getValueNames",
            "putValue",
            "removeValue");

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to process source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            changed |= removeHttpSessionContextImports(cu);
            changed |= removeObsoleteHttpSessionOverride(cu);

            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean removeHttpSessionContextImports(CompilationUnit cu) {
        return cu.getImports().removeIf(importDeclaration -> HTTP_SESSION_CONTEXT.equals(importDeclaration.getNameAsString()));
    }

    private static boolean removeObsoleteHttpSessionOverride(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!implementsHttpSession(type)) {
                continue;
            }

            for (String methodName : REMOVED_HTTP_SESSION_METHODS) {
                for (MethodDeclaration method : type.getMethodsByName(methodName).toArray(new MethodDeclaration[0])) {
                    if (isRemovedHttpSessionMethod(method)) {
                        method.remove();
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static boolean implementsHttpSession(ClassOrInterfaceDeclaration type) {
        for (var implementedType : type.getImplementedTypes()) {
            String name = implementedType.getNameAsString();
            if (HTTP_SESSION.equals(implementedType.asString()) || HTTP_SESSION.endsWith("." + name) || "HttpSession".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRemovedHttpSessionMethod(MethodDeclaration method) {
        if ("getSessionContext".equals(method.getNameAsString())) {
            String returnType = method.getType().asString();
            return method.getParameters().isEmpty()
                    && (HTTP_SESSION_CONTEXT.equals(returnType) || returnType.endsWith("HttpSessionContext"));
        }
        return true;
    }
}
