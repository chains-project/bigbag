package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    private static final String REMOVED_JASYPT_SPRING_SECURITY = "org.jasypt.spring.security";
    private static final String REMOVED_PASSWORD_ENCODER = "PasswordEncoder";
    private static final String REMOVED_PBE_PASSWORD_ENCODER = "PBEPasswordEncoder";
    private static final String ACEGI_PASSWORD_ENCODER = "org.acegisecurity.providers.encoding.PasswordEncoder";
    private static final String STRONG_PASSWORD_ENCRYPTOR = "org.jasypt.util.password.StrongPasswordEncryptor";
    private static final String STANDARD_PBE_STRING_ENCRYPTOR = "org.jasypt.encryption.pbe.StandardPBEStringEncryptor";

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        List<Path> javaFiles;
        try (Stream<Path> stream = Files.walk(root)) {
            javaFiles = stream.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .collect(Collectors.toList());
        }

        for (Path file : javaFiles) {
            transformFile(file);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        changed |= removeBrokenImports(cu);
        changed |= rewriteWrapperPatterns(cu);

        if (changed) {
            Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
        }
    }

    private static boolean removeBrokenImports(CompilationUnit cu) {
        boolean changed = false;
        for (int i = cu.getImports().size() - 1; i >= 0; i--) {
            String name = cu.getImports().get(i).getNameAsString();
            if (name.startsWith(REMOVED_JASYPT_SPRING_SECURITY + ".")) {
                cu.getImports().remove(i);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteWrapperPatterns(CompilationUnit cu) {
        boolean changed = false;
        changed |= rewriteRemovedPasswordEncoderTypes(cu);
        changed |= rewriteWrappersByInitializer(cu);

        return changed;
    }

    private static boolean rewriteWrappersByInitializer(CompilationUnit cu) {
        boolean changed = false;
        for (VariableDeclarationExpr declaration : cu.findAll(VariableDeclarationExpr.class)) {
            for (VariableDeclarator variable : declaration.getVariables()) {
                Optional<WrapperRewrite> rewrite = findWrapperRewrite(declaration, variable);
                if (rewrite.isEmpty()) {
                    continue;
                }
                applyWrapperRewrite(variable, rewrite.get());
                changed = true;
            }
        }
        return changed;
    }

    private static Optional<WrapperRewrite> findWrapperRewrite(VariableDeclarationExpr declaration,
            VariableDeclarator variable) {
        if (!variable.getInitializer().isPresent() || !variable.getInitializer().get().isObjectCreationExpr()) {
            return Optional.empty();
        }

        String wrapperName = variable.getInitializer().get().asObjectCreationExpr().getType().getNameAsString();
        if (!REMOVED_PASSWORD_ENCODER.equals(wrapperName) && !REMOVED_PBE_PASSWORD_ENCODER.equals(wrapperName)) {
            return Optional.empty();
        }

        if (!declaration.getParentNode().isPresent() || !(declaration.getParentNode().get() instanceof BlockStmt)) {
            return Optional.empty();
        }

        BlockStmt block = (BlockStmt) declaration.getParentNode().get();
        boolean seenDeclaration = false;
        for (ExpressionStmt statement : block.findAll(ExpressionStmt.class)) {
            if (!seenDeclaration) {
                if (statement.getExpression() == declaration) {
                    seenDeclaration = true;
                }
                continue;
            }

            if (!statement.getExpression().isMethodCallExpr()) {
                continue;
            }

            MethodCallExpr call = statement.getExpression().asMethodCallExpr();
            if (!call.getScope().isPresent() || !call.getScope().get().isNameExpr()) {
                continue;
            }
            if (!call.getScope().get().asNameExpr().getNameAsString().equals(variable.getNameAsString())) {
                continue;
            }

            if (REMOVED_PASSWORD_ENCODER.equals(wrapperName) && "setPasswordEncryptor".equals(call.getNameAsString())
                    && call.getArguments().size() == 1) {
                return Optional.of(new WrapperRewrite(WrapperKind.PASSWORD_ENC, call.getArguments().get(0)));
            }
            if (REMOVED_PBE_PASSWORD_ENCODER.equals(wrapperName) && "setPbeStringEncryptor".equals(call.getNameAsString())
                    && call.getArguments().size() == 1) {
                return Optional.of(new WrapperRewrite(WrapperKind.PBE_STRING_ENC, call.getArguments().get(0)));
            }
        }

        return Optional.empty();
    }

    private static void applyWrapperRewrite(VariableDeclarator variable, WrapperRewrite rewrite) {
        variable.setType(StaticJavaParser.parseType(ACEGI_PASSWORD_ENCODER));
        if (rewrite.kind == WrapperKind.PASSWORD_ENC) {
            variable.setInitializer(createPasswordEncryptorAdapter(rewrite.encryptorExpression));
            return;
        }
        variable.setInitializer(createPbeEncryptorAdapter(rewrite.encryptorExpression));
    }

    private static boolean rewriteRemovedPasswordEncoderTypes(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String rendered = type.asString();
            if (rendered.equals(REMOVED_PASSWORD_ENCODER) || rendered.endsWith("." + REMOVED_PASSWORD_ENCODER)
                    || rendered.equals(REMOVED_PBE_PASSWORD_ENCODER)
                    || rendered.endsWith("." + REMOVED_PBE_PASSWORD_ENCODER)) {
                type.replace(StaticJavaParser.parseType(ACEGI_PASSWORD_ENCODER));
                changed = true;
            }
        }

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String rendered = creation.getType().asString();
            if (rendered.equals(REMOVED_PASSWORD_ENCODER) || rendered.endsWith("." + REMOVED_PASSWORD_ENCODER)
                    || rendered.equals(REMOVED_PBE_PASSWORD_ENCODER)
                    || rendered.endsWith("." + REMOVED_PBE_PASSWORD_ENCODER)) {
                creation.getType().replace(StaticJavaParser.parseType(ACEGI_PASSWORD_ENCODER));
                changed = true;
            }
        }

        return changed;
    }

    private static Expression createPasswordEncryptorAdapter(Expression encryptorExpression) {
        String source = "new " + ACEGI_PASSWORD_ENCODER + "() {"
                + "private final " + STRONG_PASSWORD_ENCRYPTOR + " passwordEncryptor = " + encryptorExpression + ";"
                + "@Override public String encodePassword(String rawPass, Object salt) {"
                + "return passwordEncryptor.encryptPassword(rawPass); }"
                + "@Override public boolean isPasswordValid(String encPass, String rawPass, Object salt) {"
                + "return passwordEncryptor.checkPassword(rawPass, encPass); }"
                + "}";
        return StaticJavaParser.parseExpression(source);
    }

    private static Expression createPbeEncryptorAdapter(Expression encryptorExpression) {
        String source = "new " + ACEGI_PASSWORD_ENCODER + "() {"
                + "private final " + STANDARD_PBE_STRING_ENCRYPTOR + " stringEncryptor = " + encryptorExpression + ";"
                + "@Override public String encodePassword(String rawPass, Object salt) {"
                + "return stringEncryptor.encrypt(rawPass); }"
                + "@Override public boolean isPasswordValid(String encPass, String rawPass, Object salt) {"
                + "return rawPass.equals(stringEncryptor.decrypt(encPass)); }"
                + "}";
        return StaticJavaParser.parseExpression(source);
    }

    private enum WrapperKind { PASSWORD_ENC, PBE_STRING_ENC }

    private static final class WrapperRewrite {
        private final WrapperKind kind;
        private final Expression encryptorExpression;

        private WrapperRewrite(WrapperKind kind, Expression encryptorExpression) {
            this.kind = kind;
            this.encryptorExpression = encryptorExpression;
        }
    }
}
