package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {

    private static final String OLD_TYPE = "org.apache.thrift.transport.TFramedTransport";
    private static final String NEW_TYPE = "org.apache.thrift.transport.layered.TFramedTransport";
    private static final String[][] TYPE_RENAMES = {
            {"org.apache.thrift.transport.layered.TTransport", "org.apache.thrift.transport.TTransport"},
            {"org.apache.thrift.transport.layered.TIOStreamTransport", "org.apache.thrift.transport.TIOStreamTransport"},
            {"org.apache.thrift.transport.layered.TTransportException", "org.apache.thrift.transport.TTransportException"},
            {"org.apache.thrift.transport.layered.TByteBuffer", "org.apache.thrift.transport.TByteBuffer"},
            {"org.apache.thrift.transport.TFramedTransport", "org.apache.thrift.transport.layered.TFramedTransport"}
    };

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source root does not exist: " + root);
        }

        Files.walk(root)
                .filter(path -> path.toString().endsWith(".java"))
                .sorted(Comparator.naturalOrder())
                .forEach(Main::transformFile);
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            LexicalPreservingPrinter.setup(cu);

            boolean changed = false;
            changed |= rewriteImports(cu);
            changed |= rewriteTypes(cu);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
            // Skip files that fail to parse or rewrite cleanly.
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (int i = 0; i < cu.getImports().size(); i++) {
            String name = cu.getImports().get(i).getNameAsString();
            String updated = replaceType(name);
            if (!updated.equals(name)) {
                cu.getImports().get(i).setName(updated);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(Node node) {
        boolean changed = false;

        for (ClassOrInterfaceType type : node.findAll(ClassOrInterfaceType.class)) {
            String updated = replaceType(type.asString());
            if (!updated.equals(type.asString())) {
                type.replace(StaticJavaParser.parseClassOrInterfaceType(updated));
                changed = true;
            }
        }

        for (ObjectCreationExpr expr : node.findAll(ObjectCreationExpr.class)) {
            String updated = replaceType(expr.getType().asString());
            if (!updated.equals(expr.getType().asString())) {
                expr.setType(StaticJavaParser.parseClassOrInterfaceType(updated));
                changed = true;
            }
        }

        for (MethodReferenceExpr expr : node.findAll(MethodReferenceExpr.class)) {
            if ("TSerializer::new".equals(expr.toString())) {
                expr.replace(StaticJavaParser.parseExpression(
                        "() -> { try { return new org.apache.thrift.TSerializer(); } "
                                + "catch (org.apache.thrift.transport.TTransportException e) { "
                                + "throw new RuntimeException(e); } }"));
                changed = true;
            }
        }

        return changed;
    }

    private static String replaceType(String value) {
        for (String[] mapping : TYPE_RENAMES) {
            if (mapping[0].equals(value)) {
                return mapping[1];
            }
        }
        return value;
    }
}
