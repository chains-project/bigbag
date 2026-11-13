package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String OLD_TYPE = "org.mockito.runners.MockitoJUnitRunner";
    private static final String NEW_TYPE = "org.mockito.junit.MockitoJUnitRunner";
    private static final String OLD_SIMPLE_NAME = "MockitoJUnitRunner";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: Main <source-root>");
            System.exit(1);
        }

        try {
            transform(Paths.get(args[0]));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transform(Path sourceRoot) throws IOException {
        Files.walk(sourceRoot)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(Main::transformFile);
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;

            for (int i = 0; i < cu.getImports().size(); i++) {
                if (cu.getImports().get(i).getNameAsString().equals(OLD_TYPE)) {
                    cu.getImports().get(i).setName(NEW_TYPE);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (type.asString().equals(OLD_TYPE)) {
                    type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                    changed = true;
                }
            }

            for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
                if (classExpr.getType() instanceof ClassOrInterfaceType) {
                    ClassOrInterfaceType type = (ClassOrInterfaceType) classExpr.getType();
                    if (type.asString().equals(OLD_TYPE)) {
                        classExpr.setType(StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE));
                        changed = true;
                    }
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

}
