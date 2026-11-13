package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String REMOVED_TYPE = "jakarta.servlet.http.HttpSessionContext";
    private static final String SESSION_TYPE = "jakarta.servlet.http.HttpSession";
    private static final String[] REMOVED_SESSION_METHODS = {
            "getSessionContext",
            "getValue",
            "getValueNames",
            "putValue",
            "removeValue"
    };

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            System.err.println("Source root does not exist or is not a directory: " + sourceRoot);
            System.exit(1);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        try {
            Files.walk(sourceRoot)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, path));
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source root " + sourceRoot, e);
        }
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            CompilationUnit compilationUnit = parser.parse(path).getResult().orElse(null);
            if (compilationUnit == null) {
                return;
            }

            boolean changed = false;

            List<ImportDeclaration> importsToRemove = new ArrayList<>();
            for (ImportDeclaration importDeclaration : compilationUnit.getImports()) {
                if (isRemovedTypeImport(importDeclaration)) {
                    importsToRemove.add(importDeclaration);
                }
            }
            if (!importsToRemove.isEmpty()) {
                compilationUnit.getImports().removeAll(importsToRemove);
                changed = true;
            }

            List<BodyDeclaration<?>> membersToRemove = new ArrayList<>();
            for (ClassOrInterfaceDeclaration typeDeclaration : compilationUnit.findAll(ClassOrInterfaceDeclaration.class)) {
                if (implementsHttpSession(typeDeclaration)) {
                    for (MethodDeclaration methodDeclaration : typeDeclaration.getMethods()) {
                        if (isRemovedMethod(methodDeclaration.getNameAsString())) {
                            membersToRemove.add(methodDeclaration);
                        }
                    }
                }
            }
            if (!membersToRemove.isEmpty()) {
                membersToRemove.forEach(BodyDeclaration::remove);
                changed = true;
            }

            if (changed) {
                Files.write(path, compilationUnit.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean isRemovedTypeImport(ImportDeclaration importDeclaration) {
        return !importDeclaration.isAsterisk() && importDeclaration.getNameAsString().equals(REMOVED_TYPE);
    }

    private static boolean isRemovedMethod(String methodName) {
        for (String removedMethod : REMOVED_SESSION_METHODS) {
            if (removedMethod.equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRemovedType(Type type) {
        String typeName = type.asString();
        return typeName.equals(REMOVED_TYPE) || typeName.equals("HttpSessionContext");
    }

    private static boolean implementsHttpSession(ClassOrInterfaceDeclaration declaration) {
        return declaration.getImplementedTypes().stream()
                .map(Type::asString)
                .anyMatch(typeName -> typeName.equals(SESSION_TYPE) || typeName.equals("HttpSession"));
    }
}
