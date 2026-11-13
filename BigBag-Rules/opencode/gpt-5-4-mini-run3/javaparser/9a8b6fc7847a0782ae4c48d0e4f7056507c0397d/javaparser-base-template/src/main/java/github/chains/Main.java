package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_SPRING_PASSWORD_ENCODER = "org.jasypt.spring.security.PasswordEncoder";
    private static final String OLD_PBE_PASSWORD_ENCODER = "org.jasypt.spring.security.PBEPasswordEncoder";
    private static final String ACEGI_PASSWORD_ENCODER = "org.acegisecurity.providers.encoding.PasswordEncoder";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source-directory argument");
        }

        Path root = Paths.get(args[0]);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(Main::processFile);
        }
    }

    private static void processFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            boolean changed = false;

            if (cu.getImports().removeIf(i -> !i.isAsterisk() && i.getNameAsString().equals(OLD_SPRING_PASSWORD_ENCODER))) {
                changed = true;
                if (!cu.getImports().stream().anyMatch(i -> i.getNameAsString().equals(ACEGI_PASSWORD_ENCODER))) {
                    cu.addImport(ACEGI_PASSWORD_ENCODER);
                }
                changed |= replaceTypeNames(cu, "PasswordEncoder", ACEGI_PASSWORD_ENCODER);
            }

            if (cu.getImports().removeIf(i -> !i.isAsterisk() && i.getNameAsString().equals(OLD_PBE_PASSWORD_ENCODER))) {
                changed = true;
                changed |= replaceTypeNames(cu, "PBEPasswordEncoder", ACEGI_PASSWORD_ENCODER);
            }

            changed |= rewritePasswordEncoderCreations(cu);

            if (changed) {
                Files.write(file, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to transform " + file, ex);
        }
    }

    private static boolean rewritePasswordEncoderCreations(CompilationUnit cu) {
        boolean changed = false;

        for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
            String typeName = creation.getType().getNameAsString();

            if (typeName.equals("PasswordEncoder")) {
                if (replaceWithStrongPasswordEncoder(creation)) {
                    changed = true;
                }
            }

            if (typeName.equals("PBEPasswordEncoder")) {
                if (replaceWithPbePasswordEncoder(creation)) {
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static boolean replaceTypeNames(CompilationUnit cu, String simpleName, String fqcn) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.getNameAsString().equals(simpleName)) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(fqcn));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean replaceWithStrongPasswordEncoder(ObjectCreationExpr creation) {
        MethodDeclaration method = creation.findAncestor(MethodDeclaration.class).orElse(null);
        if (method == null) {
            return false;
        }

        String replacement = "new " + ACEGI_PASSWORD_ENCODER + "() {"
            + "private final org.jasypt.util.password.StrongPasswordEncryptor delegate = new org.jasypt.util.password.StrongPasswordEncryptor();"
            + "public String encodePassword(String rawPass, Object salt) { return delegate.encryptPassword(rawPass); }"
            + "public boolean isPasswordValid(String encPass, String rawPass, Object salt) { return delegate.checkPassword(rawPass, encPass); }"
            + "}";

        creation.replace(StaticJavaParser.parseExpression(replacement));
        return true;
    }

    private static boolean replaceWithPbePasswordEncoder(ObjectCreationExpr creation) {
        MethodDeclaration method = creation.findAncestor(MethodDeclaration.class).orElse(null);
        if (method == null) {
            return false;
        }

        if (method.getBody().isEmpty()) {
            return false;
        }

        String replacement = "new " + ACEGI_PASSWORD_ENCODER + "() {"
            + "private final org.jasypt.encryption.pbe.StandardPBEStringEncryptor delegate = stringEncrypter;"
            + "public String encodePassword(String rawPass, Object salt) { return delegate.encrypt(rawPass); }"
            + "public boolean isPasswordValid(String encPass, String rawPass, Object salt) { try { return rawPass.equals(delegate.decrypt(encPass)); } catch (RuntimeException ex) { return false; } }"
            + "}";

        creation.replace(StaticJavaParser.parseExpression(replacement));

        method.getBody().ifPresent(body -> {
            body.getStatements().removeIf(stmt -> stmt.isExpressionStmt()
                && shouldRemoveStatement(stmt.asExpressionStmt()));
        });

        return true;
    }

    private static boolean shouldRemoveStatement(ExpressionStmt stmt) {
        String text = stmt.toString();
        return text.contains("setPasswordEncryptor(")
            || text.contains("setPbeStringEncryptor(")
            || text.contains("setPbeByteEncryptor(");
    }
}
