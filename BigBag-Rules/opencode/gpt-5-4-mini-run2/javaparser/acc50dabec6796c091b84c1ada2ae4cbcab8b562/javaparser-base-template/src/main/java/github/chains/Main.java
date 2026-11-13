package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    private static final String OLD_FILTER = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
    private static final String NEW_FILTER = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source root path");
        }

        Path root = Paths.get(args[0]);
        SourceRoot sourceRoot = new SourceRoot(root);
        List<CompilationUnit> units = sourceRoot.tryToParse().stream()
                .filter(result -> result.getResult().isPresent())
                .map(result -> result.getResult().get())
                .collect(Collectors.toList());

        for (CompilationUnit unit : units) {
            rewriteUnit(unit);
        }

        sourceRoot.saveAll(root);
    }

    private static boolean rewriteUnit(CompilationUnit unit) {
        boolean changed = false;
        changed |= replaceImport(unit);
        changed |= replaceTypeUsages(unit);
        if (changed) {
            LexicalPreservingPrinter.setup(unit);
        }
        return changed;
    }

    private static boolean replaceImport(CompilationUnit unit) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : unit.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_FILTER)) {
                importDeclaration.setName(NEW_FILTER);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceTypeUsages(Node node) {
        boolean changed = false;
        for (ClassOrInterfaceType type : node.findAll(ClassOrInterfaceType.class)) {
            if (type.asString().equals(OLD_FILTER)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_FILTER));
                changed = true;
            }
        }
        return changed;
    }
}
