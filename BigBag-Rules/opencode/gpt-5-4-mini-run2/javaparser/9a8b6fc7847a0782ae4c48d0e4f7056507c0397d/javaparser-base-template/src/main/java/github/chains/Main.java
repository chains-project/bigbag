package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
    private static final String ACEGI_PASSWORD_ENCODER = "org.acegisecurity.providers.encoding.PasswordEncoder";

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to walk source tree: " + root, e);
        }
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit unit = StaticJavaParser.parse(path, StandardCharsets.UTF_8);
            if (transformUnit(unit)) {
                Files.writeString(path, unit.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean transformUnit(CompilationUnit unit) {
        boolean removedJasyptPasswordEncoder = unit.getImports().removeIf(importDecl -> {
            String name = importDecl.getNameAsString();
            return name.equals("org.jasypt.spring.security.PasswordEncoder")
                    || name.equals("org.jasypt.spring.security.PBEPasswordEncoder");
        });
        boolean changed = removedJasyptPasswordEncoder;

        if (removedJasyptPasswordEncoder && unit.getImports().stream().noneMatch(i -> i.getNameAsString().equals(ACEGI_PASSWORD_ENCODER))) {
            unit.addImport(ACEGI_PASSWORD_ENCODER);
            changed = true;
        }

        for (BlockStmt block : unit.findAll(BlockStmt.class)) {
            changed |= transformBlock(block);
        }

        return changed;
    }

    private static boolean transformBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> statements = new ArrayList<>(block.getStatements());
        for (int i = 0; i + 2 < statements.size(); i++) {
            Optional<Statement> replacement = rewritePasswordEncoder(statements, i);
            if (replacement.isEmpty()) {
                replacement = rewritePbePasswordEncoder(statements, i);
            }
            if (replacement.isPresent()) {
                block.getStatements().subList(i, i + 3).clear();
                block.addStatement(i, replacement.get());
                statements = new ArrayList<>(block.getStatements());
                changed = true;
                i = Math.max(-1, i - 1);
            }
        }
        return changed;
    }

    private static Optional<Statement> rewritePasswordEncoder(List<Statement> statements, int index) {
        return rewrite(statements, index,
                "PasswordEncoder",
                "setPasswordEncryptor",
                "org.jasypt.util.password.PasswordEncryptor",
                "delegate.encryptPassword(rawPass)",
                "delegate.checkPassword(rawPass, encPass)");
    }

    private static Optional<Statement> rewritePbePasswordEncoder(List<Statement> statements, int index) {
        return rewrite(statements, index,
                "PBEPasswordEncoder",
                "setPbeStringEncryptor",
                "org.jasypt.encryption.pbe.StandardPBEStringEncryptor",
                "delegate.encrypt(rawPass)",
                "rawPass.equals(delegate.decrypt(encPass))");
    }

    private static Optional<Statement> rewrite(
            List<Statement> statements,
            int index,
            String removedType,
            String setterName,
            String delegateType,
            String encodeBody,
            String matchesBody) {

        Statement first = statements.get(index);
        Statement second = statements.get(index + 1);
        Statement third = statements.get(index + 2);

        if (!first.isExpressionStmt() || !second.isExpressionStmt() || !third.isReturnStmt()) {
            return Optional.empty();
        }

        Expression firstExpr = first.asExpressionStmt().getExpression();
        if (!firstExpr.isVariableDeclarationExpr()) {
            return Optional.empty();
        }

        VariableDeclarationExpr declaration = firstExpr.asVariableDeclarationExpr();
        if (declaration.getVariables().size() != 1) {
            return Optional.empty();
        }

        VariableDeclarator variable = declaration.getVariable(0);
        if (!variable.getType().asString().equals(removedType)) {
            return Optional.empty();
        }
        if (variable.getInitializer().isEmpty() || !variable.getInitializer().get().isObjectCreationExpr()) {
            return Optional.empty();
        }

        ObjectCreationExpr creation = variable.getInitializer().get().asObjectCreationExpr();
        if (!creation.getType().getNameAsString().equals(removedType)) {
            return Optional.empty();
        }

        Expression secondExpr = second.asExpressionStmt().getExpression();
        if (!secondExpr.isMethodCallExpr()) {
            return Optional.empty();
        }

        MethodCallExpr setter = secondExpr.asMethodCallExpr();
        if (!setter.getNameAsString().equals(setterName) || setter.getArguments().size() != 1) {
            return Optional.empty();
        }
        if (setter.getScope().isEmpty() || !setter.getScope().get().isNameExpr()) {
            return Optional.empty();
        }
        if (!setter.getScope().get().asNameExpr().getNameAsString().equals(variable.getNameAsString())) {
            return Optional.empty();
        }

        ReturnStmt ret = third.asReturnStmt();
        if (ret.getExpression().isEmpty() || !ret.getExpression().get().isNameExpr()) {
            return Optional.empty();
        }
        if (!ret.getExpression().get().asNameExpr().getNameAsString().equals(variable.getNameAsString())) {
            return Optional.empty();
        }

        String delegateExpression = setter.getArgument(0).toString();
        String code = "return new " + ACEGI_PASSWORD_ENCODER + "() {"
                + "private final " + delegateType + " delegate = " + delegateExpression + ";"
                + "@Override public String encodePassword(String rawPass, Object salt) { return " + encodeBody + "; }"
                + "@Override public boolean isPasswordValid(String encPass, String rawPass, Object salt) { return " + matchesBody + "; }"
                + "};";

        return Optional.of(StaticJavaParser.parseStatement(code));
    }
}
