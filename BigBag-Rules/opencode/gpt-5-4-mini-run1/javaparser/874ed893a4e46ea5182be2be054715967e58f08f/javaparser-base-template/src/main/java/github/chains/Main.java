package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_OBJECT_MAPPER_FQN = "com.fasterxml.jackson.databind.ObjectMapper";
    private static final String LAISSEZ_FAIRE_VALIDATOR_FQN = "com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator";
    private static final String LAISSEZ_FAIRE_INSTANCE = LAISSEZ_FAIRE_VALIDATOR_FQN + ".instance";
    private static final String ENABLE_DEFAULT_TYPING = "enableDefaultTyping";
    private static final String ENABLE_DEFAULT_TYPING_AS_PROPERTY = "enableDefaultTypingAsProperty";
    private static final String ACTIVATE_DEFAULT_TYPING = "activateDefaultTyping";
    private static final String ACTIVATE_DEFAULT_TYPING_AS_PROPERTY = "activateDefaultTypingAsProperty";
    private static final String DEACTIVATE_DEFAULT_TYPING = "deactivateDefaultTyping";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        Path inputRoot = Path.of(args[0]);
        Path outputRoot = args.length > 1 ? Path.of(args[1]) : inputRoot;

        try {
            transformTree(inputRoot, outputRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform Java sources", e);
        }
    }

    private static void transformTree(Path inputRoot, Path outputRoot) throws IOException {
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        try (Stream<Path> paths = Files.walk(inputRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> transformFile(parser, inputRoot, outputRoot, path));
        }
    }

    private static void transformFile(JavaParser parser, Path inputRoot, Path outputRoot, Path sourceFile) {
        try {
            CompilationUnit cu = parser.parse(sourceFile).getResult().orElse(null);
            if (cu == null) {
                return;
            }

            DefaultTypingMigrationVisitor visitor = new DefaultTypingMigrationVisitor();
            visitor.visit(cu, null);
            if (!visitor.isChanged()) {
                if (!inputRoot.equals(outputRoot)) {
                    Path targetFile = outputRoot.resolve(inputRoot.relativize(sourceFile));
                    Files.createDirectories(targetFile.getParent());
                    Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }

            Path targetFile = outputRoot.resolve(inputRoot.relativize(sourceFile));
            Files.createDirectories(targetFile.getParent());
            Files.writeString(targetFile, cu.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + sourceFile, e);
        }
    }

    private static final class DefaultTypingMigrationVisitor extends ModifierVisitor<Void> {
        private boolean changed;

        boolean isChanged() {
            return changed;
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (!isCandidate(n)) {
                return super.visit(n, arg);
            }

            Optional<Expression> scope = n.getScope();
            if (scope.isEmpty()) {
                return super.visit(n, arg);
            }

            if (!isJacksonObjectMapperCall(n, scope.get())) {
                return super.visit(n, arg);
            }

            switch (n.getNameAsString()) {
                case ENABLE_DEFAULT_TYPING:
                    n.setName(ACTIVATE_DEFAULT_TYPING);
                    n.getArguments().add(0, new com.github.javaparser.ast.expr.NameExpr(LAISSEZ_FAIRE_INSTANCE));
                    changed = true;
                    break;
                case ENABLE_DEFAULT_TYPING_AS_PROPERTY:
                    n.setName(ACTIVATE_DEFAULT_TYPING_AS_PROPERTY);
                    n.getArguments().add(0, new com.github.javaparser.ast.expr.NameExpr(LAISSEZ_FAIRE_INSTANCE));
                    changed = true;
                    break;
                default:
                    return super.visit(n, arg);
            }

            return super.visit(n, arg);
        }

        private boolean isCandidate(MethodCallExpr call) {
            String name = call.getNameAsString();
            return ENABLE_DEFAULT_TYPING.equals(name)
                    || ENABLE_DEFAULT_TYPING_AS_PROPERTY.equals(name)
                    || DEACTIVATE_DEFAULT_TYPING.equals(name);
        }

        private boolean isJacksonObjectMapperCall(MethodCallExpr call, Expression scope) {
            if (scope.toString().contains("ObjectMapper")) {
                return true;
            }

            CompilationUnit cu = call.findCompilationUnit().orElse(null);
            if (cu == null) {
                return false;
            }

            return cu.getImports().stream().anyMatch(importDeclaration -> {
                if (importDeclaration.isAsterisk()) {
                    return "com.fasterxml.jackson.databind".equals(importDeclaration.getNameAsString());
                }
                return OLD_OBJECT_MAPPER_FQN.equals(importDeclaration.getNameAsString());
            });
        }
    }
}
