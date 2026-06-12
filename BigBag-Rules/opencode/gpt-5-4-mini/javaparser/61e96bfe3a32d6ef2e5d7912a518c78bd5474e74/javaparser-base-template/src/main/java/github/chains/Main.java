package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {
    private static final String OLD_FNATP = "SizeAndTimeBasedFNATP";
    private static final String OLD_FNATP_FQN = "ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP";
    private static final String OLD_POLICY = "TimeBasedRollingPolicy";
    private static final String OLD_POLICY_FQN = "ch.qos.logback.core.rolling.TimeBasedRollingPolicy";
    private static final String NEW_POLICY = "SizeAndTimeBasedRollingPolicy";
    private static final String NEW_POLICY_FQN = "ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path root = Paths.get(args[0]);
        JavaParser parser = new JavaParser(new ParserConfiguration());

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        rewriteFile(parser, path);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to rewrite " + path, e);
                    }
                });
    }

    private static void rewriteFile(JavaParser parser, Path file) throws IOException {
        Optional<CompilationUnit> parsed = parser.parse(file).getResult();
        if (parsed.isEmpty()) {
            return;
        }

        CompilationUnit cu = parsed.get();
        LexicalPreservingPrinter.setup(cu);

        cu.getImports().forEach(importDecl -> {
            String name = importDecl.getNameAsString();
            if (OLD_FNATP_FQN.equals(name)) {
                importDecl.setName(NEW_POLICY_FQN);
            }
        });

        cu.findAll(ClassOrInterfaceType.class).forEach(type -> {
            if (matches(type, OLD_FNATP) || matches(type, OLD_FNATP_FQN)) {
                type.setName(NEW_POLICY);
            } else if (matches(type, OLD_POLICY) || matches(type, OLD_POLICY_FQN)) {
                type.setName(NEW_POLICY);
            }
        });

        cu.findAll(VariableDeclarator.class).forEach(vd -> {
            if (matches(vd.getType(), OLD_FNATP) || matches(vd.getType(), OLD_FNATP_FQN)
                    || matches(vd.getType(), OLD_POLICY) || matches(vd.getType(), OLD_POLICY_FQN)) {
                vd.setType(new ClassOrInterfaceType(null, NEW_POLICY));
            }
        });

        cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
            if (matches(creation.getType(), OLD_FNATP) || matches(creation.getType(), OLD_FNATP_FQN)
                    || matches(creation.getType(), OLD_POLICY) || matches(creation.getType(), OLD_POLICY_FQN)) {
                creation.setType(new ClassOrInterfaceType(null, NEW_POLICY));
            }
        });

        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(Main::rewriteEncoderClass);

        cu.findAll(MethodCallExpr.class).forEach(call -> {
            if (call.getNameAsString().equals("setTimeBasedFileNamingAndTriggeringPolicy")) {
                call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
                return;
            }
            if (call.getNameAsString().equals("setTimeBasedRollingPolicy")) {
                call.findAncestor(ExpressionStmt.class).ifPresent(ExpressionStmt::remove);
            }
        });

        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static boolean matches(Type type, String expected) {
        String actual = type.asString();
        return expected.equals(actual) || expected.equals(type.getElementType().asString()) || actual.endsWith("." + expected);
    }

    private static boolean matches(ClassOrInterfaceType type, String expected) {
        String actual = type.asString();
        return expected.equals(actual) || expected.equals(type.getNameAsString()) || actual.endsWith("." + expected);
    }

    private static boolean matches(Expression expression, String expected) {
        return expression.isNameExpr() && expected.equals(expression.asNameExpr().getNameAsString());
    }

    private static void rewriteEncoderClass(ClassOrInterfaceDeclaration declaration) {
        boolean extendsEncoderBase = declaration.getExtendedTypes().stream()
                .anyMatch(type -> type.getNameAsString().equals("EncoderBase") || type.asString().equals("ch.qos.logback.core.encoder.EncoderBase"));
        if (!extendsEncoderBase) {
            return;
        }

        declaration.getMethodsByName("doEncode").forEach(method -> {
            method.setName("encode");
            method.setType(StaticJavaParser.parseType("byte[]"));
            ensureReturn(method);
        });

        if (declaration.getMethodsByName("headerBytes").isEmpty()) {
            declaration.addMember(StaticJavaParser.parseBodyDeclaration("public byte[] headerBytes() { return null; }").asMethodDeclaration());
        }
        if (declaration.getMethodsByName("footerBytes").isEmpty()) {
            declaration.addMember(StaticJavaParser.parseBodyDeclaration("public byte[] footerBytes() { return null; }").asMethodDeclaration());
        }
    }

    private static void ensureReturn(MethodDeclaration method) {
        BlockStmt body = method.getBody().orElse(null);
        if (body == null || !body.getStatements().isEmpty() && body.getStatement(body.getStatements().size() - 1).isReturnStmt()) {
            return;
        }
        body.addStatement("return new byte[0];");
    }
}
