package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class Main {
    private static final String OLD_FRAMED = "org.apache.thrift.transport.TFramedTransport";
    private static final String NEW_FRAMED = "org.apache.thrift.transport.layered.TFramedTransport";
    private static final String OLD_FAST_FRAMED = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_FAST_FRAMED = "org.apache.thrift.transport.layered.TFastFramedTransport";

    public static void main(String[] args) throws IOException {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        try (var stream = Files.walk(root)) {
            stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .filter(p -> !p.toString().contains("/target/"))
                    .sorted(Comparator.naturalOrder())
                    .forEach(Main::transform);
        }
    }

    private static void transform(Path file) {
        try {
            String src = Files.readString(file, StandardCharsets.UTF_8);
            if (!src.contains("TFramedTransport") && !src.contains("TFastFramedTransport") && !src.contains("TProtocol")) {
                return;
            }
            CompilationUnit cu = StaticJavaParser.parse(src);
            LexicalPreservingPrinter.setup(cu);
            boolean changed = false;

            for (ImportDeclaration id : cu.getImports()) {
                if (id.getNameAsString().equals(OLD_FRAMED)) {
                    id.setName(NEW_FRAMED);
                    changed = true;
                }
                if (id.getNameAsString().equals(OLD_FAST_FRAMED)) {
                    id.setName(NEW_FAST_FRAMED);
                    changed = true;
                }
            }

            for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
                if (type.asString().equals("TFramedTransport")) {
                    type.setName("TFramedTransport");
                    changed = true;
                }
            }

            for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                if (clazz.getExtendedTypes().stream().anyMatch(t -> t.asString().endsWith("TFramedTransport"))) {
                    for (ConstructorDeclaration ctor : clazz.getConstructors()) {
                        if (ctor.getThrownExceptions().stream().noneMatch(t -> t.asString().equals("org.apache.thrift.transport.TTransportException"))) {
                            ctor.addThrownException(StaticJavaParser.parseClassOrInterfaceType("org.apache.thrift.transport.TTransportException"));
                            changed = true;
                        }
                    }
                }

                if (clazz.getExtendedTypes().stream().anyMatch(t -> t.asString().endsWith("TProtocol"))) {
                    boolean has = clazz.getMethodsByName("getMinSerializedSize").stream()
                            .anyMatch(m -> m.getParameters().size() == 1 && m.getParameter(0).getType().asString().equals("byte"));
                    if (!has) {
                        clazz.addMember(StaticJavaParser.parseBodyDeclaration(
                                "public int getMinSerializedSize(byte type) throws org.apache.thrift.TException {"
                                        + " switch (type) {"
                                        + " case 0:"
                                        + " case 1: return 0;"
                                        + " case 2: return 1;"
                                        + " case 3: return 1;"
                                        + " case 4: return 8;"
                                        + " case 5: return 2;"
                                        + " case 6: return 4;"
                                        + " case 7: return 8;"
                                        + " case 8: return 4;"
                                        + " case 9: return 0;"
                                        + " case 10: return 4;"
                                        + " case 11: return 4;"
                                        + " case 12: return 4;"
                                        + " case 13: return 4;"
                                        + " case 14: return 4;"
                                        + " case 15: return 4;"
                                        + " default: throw new org.apache.thrift.TException(\"unrecognized type code\");"
                                        + " }"
                                        + " }"));
                        changed = true;
                    }
                }
            }

            if (changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
    }
}
