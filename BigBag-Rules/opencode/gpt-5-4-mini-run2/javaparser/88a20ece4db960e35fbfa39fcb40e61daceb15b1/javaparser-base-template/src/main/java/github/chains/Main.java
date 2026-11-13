package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.VoidType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    private static final String OLD_PUBLISH_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_PUBLISH_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String PARTITION_PUBLISHER_FACTORY =
            "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";
    private static final String PARTITION_TYPE = "com.google.cloud.pubsublite.Partition";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument.");
        }

        Path sourceRoot = Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(sourceRoot)) {
            stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            transformFile(file);
        }
    }

    private static void transformFile(Path file) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(original);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        changed |= replacePublishMetadata(cu);
        changed |= replacePartitionPublisherFactoryLambdas(cu);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean replacePublishMetadata(CompilationUnit cu) {
        boolean changed = false;

        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_PUBLISH_METADATA)) {
                importDeclaration.setName(NEW_PUBLISH_METADATA);
                changed = true;
            }
        }

        for (com.github.javaparser.ast.type.ClassOrInterfaceType type : cu.findAll(com.github.javaparser.ast.type.ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals("PublishMetadata")) {
                type.setName(new SimpleName("Offset"));
                changed = true;
            }
        }

        return changed;
    }

    private static boolean replacePartitionPublisherFactoryLambdas(CompilationUnit cu) {
        boolean changed = false;
        for (LambdaExpr lambda : cu.findAll(LambdaExpr.class)) {
            Optional<MethodCallExpr> call = lambda.findAncestor(MethodCallExpr.class);
            if (call.isEmpty() || !call.get().getNameAsString().equals("setPublisherFactory")) {
                continue;
            }
            if (!isArgumentOf(call.get(), lambda)) {
                continue;
            }
            if (lambda.getParameters().size() != 1) {
                continue;
            }

            String paramName = lambda.getParameter(0).getNameAsString();
            String bodySource = renderLambdaBody(lambda);
            String replacement =
                    "new "
                            + PARTITION_PUBLISHER_FACTORY
                            + "() {"
                            + " @Override public "
                            + "com.google.cloud.pubsublite.internal.Publisher<com.google.cloud.pubsublite.MessageMetadata>"
                            + " newPublisher(" + PARTITION_TYPE + " " + paramName + ") "
                            + bodySource
                            + " @Override public void close() {} }";
            lambda.replace(StaticJavaParser.parseExpression(replacement));
            changed = true;
        }
        return changed;
    }

    private static boolean isArgumentOf(MethodCallExpr call, LambdaExpr lambda) {
        for (Expression arg : call.getArguments()) {
            if (arg == lambda) {
                return true;
            }
        }
        return false;
    }

    private static String renderLambdaBody(LambdaExpr lambda) {
        if (lambda.getBody().isBlockStmt()) {
            BlockStmt block = lambda.getBody().asBlockStmt();
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            for (var statement : block.getStatements()) {
                builder.append(" ").append(statement.toString());
            }
            if (!block.getStatements().isEmpty()) {
                builder.append(" ");
            }
            builder.append("}");
            return builder.toString();
        }
        return "{ return " + lambda.getExpressionBody().orElseThrow().toString() + "; }";
    }

}
