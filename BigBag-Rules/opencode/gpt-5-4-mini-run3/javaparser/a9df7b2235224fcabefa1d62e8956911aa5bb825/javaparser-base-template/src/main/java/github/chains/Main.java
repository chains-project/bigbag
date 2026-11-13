package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final String YAML_FQCN = "org.yaml.snakeyaml.Yaml";
    private static final String LOADER_OPTIONS_FQCN = "org.yaml.snakeyaml.LoaderOptions";
    private static final String DUMPER_OPTIONS_FQCN = "org.yaml.snakeyaml.DumperOptions";
    private static final String REPRESENTER_FQCN = "org.yaml.snakeyaml.representer.Representer";
    private static final String SCALAR_STYLE_FQCN = "org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN";
    private static final String INTROSPECTION_EXCEPTION_FQCN = "java.beans.IntrospectionException";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        List<Path> javaFiles;
        try (var stream = Files.walk(sourceRoot)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java")).toList();
        }

        for (Path javaFile : javaFiles) {
            transform(javaFile);
        }
    }

    private static void transform(Path javaFile) throws IOException {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(javaFile, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IOException("Failed to parse " + javaFile, e);
        }

        LexicalPreservingPrinter.setup(cu);
        boolean[] changed = {false};

        new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ObjectCreationExpr expr, Void arg) {
                super.visit(expr, arg);

                if (isType(expr, YAML_FQCN) && expr.getArguments().size() == 4) {
                    expr.getArguments().add(3, StaticJavaParser.parseExpression("new " + LOADER_OPTIONS_FQCN + "()"));
                    changed[0] = true;
                }

                if (isType(expr, DUMPER_OPTIONS_FQCN)) {
                    changed[0] |= ensureScalarStyleAfterDumperOptions(expr);
                }
            }

            @Override
            public void visit(ConstructorDeclaration ctor, Void arg) {
                super.visit(ctor, arg);
                if (needsRepresenterScalarStyle(ctor) && ensureCtorScalarStyle(ctor)) {
                    changed[0] = true;
                }
            }

            @Override
            public void visit(MethodDeclaration method, Void arg) {
                super.visit(method, arg);
                if (method.getThrownExceptions().removeIf(t -> t.asString().equals(INTROSPECTION_EXCEPTION_FQCN)
                        || t.asString().endsWith("IntrospectionException"))) {
                    changed[0] = true;
                }
            }
        }.visit(cu, null);

        if (changed[0]) {
            Files.writeString(javaFile, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean isType(ObjectCreationExpr expr, String fqcn) {
        String typeName = expr.getType().asString();
        return fqcn.equals(typeName) || typeName.equals(simpleName(fqcn));
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx < 0 ? fqcn : fqcn.substring(idx + 1);
    }

    private static boolean needsRepresenterScalarStyle(ConstructorDeclaration ctor) {
        if (ctor.getBody().toString().contains("setDefaultScalarStyle")) {
            return false;
        }

        return ctor.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(clazz -> clazz.getExtendedTypes().stream()
                        .anyMatch(type -> type.getNameAsString().equals("Representer") || type.getNameAsString().endsWith("Representer")))
                .orElse(false);
    }

    private static boolean ensureCtorScalarStyle(ConstructorDeclaration ctor) {
        Statement setter = StaticJavaParser.parseStatement("setDefaultScalarStyle(" + SCALAR_STYLE_FQCN + ");");
        BlockStmt body = ctor.getBody();
        NodeList<Statement> statements = body.getStatements();
        int insertAt = 0;
        if (!statements.isEmpty() && statements.get(0).isExplicitConstructorInvocationStmt()) {
            insertAt = 1;
        }
        statements.add(insertAt, setter);
        return true;
    }

    private static boolean ensureScalarStyleAfterDumperOptions(ObjectCreationExpr expr) {
        if (!(expr.getParentNode().orElse(null) instanceof VariableDeclarationExpr)) {
            return false;
        }
        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) expr.getParentNode().orElse(null);
        if (varDecl.getVariables().size() != 1) {
            return false;
        }
        if (!(varDecl.getParentNode().orElse(null) instanceof ExpressionStmt)) {
            return false;
        }
        ExpressionStmt stmt = (ExpressionStmt) varDecl.getParentNode().orElse(null);
        if (!(stmt.getParentNode().orElse(null) instanceof BlockStmt)) {
            return false;
        }
        BlockStmt block = (BlockStmt) stmt.getParentNode().orElse(null);

        String varName = varDecl.getVariable(0).getNameAsString();
        String targetCall = varName + ".setDefaultScalarStyle(" + SCALAR_STYLE_FQCN + ");";
        if (block.toString().contains(targetCall)) {
            return false;
        }

        Statement setter = StaticJavaParser.parseStatement(targetCall);
        int index = block.getStatements().indexOf(stmt);
        if (index >= 0) {
            block.getStatements().add(index + 1, setter);
            return true;
        }
        return false;
    }
}
