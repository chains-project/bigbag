package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_TYPE = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_SIMPLE = "JsonBranch";
    private static final String NEW_SIMPLE = "DataElement";
    private static final String OLD_METHOD = "getJsonPath";
    private static final String NEW_METHOD = "getPath";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected one argument: source directory");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }
    }

    private static void transformFile(Path path) {
        try {
            String original = Files.readString(path);
            CompilationUnit cu = StaticJavaParser.parse(original);
            JsonBranchToDataElementRule rule = new JsonBranchToDataElementRule();
            rule.visit(cu, null);
            if (rule.changed) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static final class JsonBranchToDataElementRule extends VoidVisitorAdapter<Void> {
        private boolean changed;

        @Override
        public void visit(CompilationUnit cu, Void arg) {
            cu.getImports().forEach(importDecl -> {
                if (importDecl.getNameAsString().equals(OLD_TYPE)) {
                    importDecl.setName(StaticJavaParser.parseName(NEW_TYPE));
                    changed = true;
                }
            });
            super.visit(cu, arg);
        }

        @Override
        public void visit(Parameter n, Void arg) {
            replaceType(n.getType());
            super.visit(n, arg);
        }

        @Override
        public void visit(ObjectCreationExpr n, Void arg) {
            replaceType(n.getType());
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            if (OLD_METHOD.equals(n.getNameAsString())) {
                n.setName(NEW_METHOD);
                changed = true;
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            replaceType(n);
            super.visit(n, arg);
        }

        private void replaceType(ClassOrInterfaceType type) {
            if (type == null) {
                return;
            }

            String name = type.getNameAsString();
            if (OLD_SIMPLE.equals(name) || OLD_TYPE.equals(type.toString())) {
                type.setName(new SimpleName(NEW_SIMPLE));
                if (type.getScope().isPresent()) {
                    type.removeScope();
                }
                changed = true;
            }
        }

        private void replaceType(Type type) {
            if (type instanceof ClassOrInterfaceType) {
                replaceType((ClassOrInterfaceType) type);
            }
        }
    }
}
