package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_SESSION_IMPLEMENTOR = "org.hibernate.engine.spi.SessionImplementor";
    private static final String NEW_SESSION_IMPLEMENTOR = "org.hibernate.engine.spi.SharedSessionContractImplementor";
    private static final String[] CHANGED_METHOD_NAMES = {"nullSafeGet", "nullSafeSet"};

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
                if (!isChangedMethod(method)) {
                    continue;
                }

                for (Parameter parameter : method.getParameters()) {
                    if (isOldSessionImplementor(parameter.getType())) {
                        parameter.setType(NEW_SESSION_IMPLEMENTOR);
                        changed = true;
                    }
                }
            }

            if (changed) {
                replaceImport(cu, OLD_SESSION_IMPLEMENTOR, NEW_SESSION_IMPLEMENTOR);
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static boolean isChangedMethod(MethodDeclaration method) {
        for (String name : CHANGED_METHOD_NAMES) {
            if (method.getNameAsString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOldSessionImplementor(Type type) {
        String text = type.toString();
        return OLD_SESSION_IMPLEMENTOR.equals(text) || "SessionImplementor".equals(text);
    }

    private static void replaceImport(CompilationUnit cu, String oldType, String newType) {
        List<ImportDeclaration> imports = cu.getImports();
        boolean hasOldImport = false;
        for (ImportDeclaration importDeclaration : imports) {
            if (importDeclaration.getNameAsString().equals(oldType)) {
                importDeclaration.setName(newType);
                hasOldImport = true;
            }
        }

        if (!hasOldImport && cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(newType))) {
            cu.addImport(newType);
        }
    }
}
