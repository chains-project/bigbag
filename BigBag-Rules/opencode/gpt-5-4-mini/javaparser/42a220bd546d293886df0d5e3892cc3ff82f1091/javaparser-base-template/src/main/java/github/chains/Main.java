package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String OLD_TYPE = "org.apache.commons.codec.digest.DigestUtils";
    private static final String OLD_METHOD = "md5Hex";
    private static final String HELPER_METHOD = "md5HexGenerated";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root does not exist: " + sourceRoot);
        }

        List<Path> javaFiles = new ArrayList<>();
        try (var paths = Files.walk(sourceRoot)) {
            paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(javaFiles::add);
        }

        for (Path file : javaFiles) {
            transform(file);
        }
    }

    private static void transform(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        Set<ClassOrInterfaceDeclaration> classesNeedingHelper = new LinkedHashSet<>();
        final boolean[] changed = {false};

        new VoidVisitorAdapter<Set<ClassOrInterfaceDeclaration>>() {
            @Override
            public void visit(MethodCallExpr call, Set<ClassOrInterfaceDeclaration> ctx) {
                super.visit(call, ctx);

                if (!OLD_METHOD.equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                    return;
                }

                if (call.getScope().isEmpty() || !call.getScope().get().toString().endsWith("DigestUtils")) {
                    return;
                }

                ClassOrInterfaceDeclaration owner = call.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null);
                if (owner == null) {
                    return;
                }

                call.replace(new MethodCallExpr(null, new SimpleName(HELPER_METHOD), NodeList.nodeList(call.getArgument(0).clone())));
                ctx.add(owner);
                changed[0] = true;
            }
        }.visit(cu, classesNeedingHelper);

        if (!changed[0]) {
            return;
        }

        cu.getImports().removeIf(imp -> imp.getNameAsString().equals(OLD_TYPE));

        for (ClassOrInterfaceDeclaration type : classesNeedingHelper) {
            ensureHelper(type);
        }

        Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }

    private static void ensureHelper(ClassOrInterfaceDeclaration type) {
        if (type.getMethodsByName(HELPER_METHOD).stream().anyMatch(method -> method.getParameters().size() == 1)) {
            return;
        }

        MethodDeclaration helper = StaticJavaParser.parseBodyDeclaration(String.join("\n",
                "private static String " + HELPER_METHOD + "(String value) {",
                "    try {",
                "        byte[] digest = java.security.MessageDigest.getInstance(\"MD5\")",
                "                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));",
                "        StringBuilder hex = new StringBuilder(digest.length * 2);",
                "        for (byte b : digest) {",
                "            int unsigned = b & 0xFF;",
                "            if (unsigned < 16) {",
                "                hex.append('0');",
                "            }",
                "            hex.append(java.lang.Integer.toHexString(unsigned));",
                "        }",
                "        return hex.toString();",
                "    } catch (java.security.NoSuchAlgorithmException e) {",
                "        throw new java.lang.IllegalStateException(e);",
                "    }",
                "}"))
                .asMethodDeclaration();

        type.addMember(helper);
    }
}
