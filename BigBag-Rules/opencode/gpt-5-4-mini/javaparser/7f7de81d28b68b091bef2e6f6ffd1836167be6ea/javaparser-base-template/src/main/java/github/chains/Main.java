package github.chains;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.WildcardType;
import com.github.javaparser.utils.SourceRoot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";
    private static final NodeList<Type> WILDCARD_TYPE_ARGUMENTS = NodeList.nodeList(new WildcardType());

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("usage: Main <source-root>");
        }

        Path sourceRootPath = Paths.get(args[0]).toAbsolutePath();
        if (!Files.isDirectory(sourceRootPath)) {
            throw new IllegalArgumentException("source root does not exist: " + sourceRootPath);
        }

        SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        sourceRoot.tryToParseParallelized();

        for (CompilationUnit compilationUnit : sourceRoot.getCompilationUnits()) {
            boolean changed = false;

            for (ClassOrInterfaceType type : compilationUnit.findAll(ClassOrInterfaceType.class)) {
                if (shouldWildcard(compilationUnit, type)) {
                    type.setTypeArguments(WILDCARD_TYPE_ARGUMENTS);
                    changed = true;
                }
            }

            if (changed) {
                compilationUnit.getStorage().ifPresent(storage -> storage.save());
            }
        }
    }

    private static boolean shouldWildcard(CompilationUnit compilationUnit, ClassOrInterfaceType type) {
        if (type.getTypeArguments().isPresent()) {
            return false;
        }

        if (type.getParentNode().filter(ClassOrInterfaceDeclaration.class::isInstance).isPresent()) {
            return false;
        }

        if (!"ManagedObject".equals(type.getNameAsString())) {
            return false;
        }

        boolean importedOrQualified = type.getScope().map(scope -> TARGET_TYPE.equals(scope.toString())).orElse(false)
                || compilationUnit.getImports().stream().anyMatch(importDeclaration -> {
            String imported = importDeclaration.getNameAsString();
            return imported.equals(TARGET_TYPE) || (importDeclaration.isAsterisk() && TARGET_TYPE.startsWith(imported + "."));
        });

        if (!importedOrQualified) {
            return false;
        }

        return type.getParentNode().flatMap(Node::getParentNode).filter(ClassOrInterfaceType.class::isInstance).isPresent();

    }
}
