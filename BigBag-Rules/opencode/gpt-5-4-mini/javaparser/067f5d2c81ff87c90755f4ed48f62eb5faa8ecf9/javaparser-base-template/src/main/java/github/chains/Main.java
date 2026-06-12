package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.TryStmt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {

    private static final String THRIFT_SERIALIZER = "org.apache.thrift.TSerializer";
    private static final String THRIFT_DESERIALIZER = "org.apache.thrift.TDeserializer";
    private static final String THRIFT_FRAMED_TRANSPORT = "org.apache.thrift.transport.TFramedTransport";
    private static final String THRIFT_IO_STREAM_TRANSPORT = "org.apache.thrift.transport.TIOStreamTransport";
    private static final String THRIFT_MEMORY_INPUT_TRANSPORT = "org.apache.thrift.transport.TMemoryInputTransport";
    private static final String THRIFT_MEMORY_BUFFER = "org.apache.thrift.transport.TMemoryBuffer";
    private static final String OLD_FAST_FRAMED_TRANSPORT = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_FAST_FRAMED_TRANSPORT = "org.apache.thrift.transport.layered.TFastFramedTransport";
    private static final String THRIFT_TRANSPORT = "org.apache.thrift.transport.TTransport";
    private static final String THRIFT_PROTOCOL_FACTORY = "org.apache.thrift.protocol.TProtocolFactory";
    private static final String THRIFT_TRANSPORT_EXCEPTION = "org.apache.thrift.transport.TTransportException";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");

        try {
            List<Path> javaFiles;
            try (var walk = Files.walk(sourceRoot)) {
                javaFiles = walk.filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
            }

            for (Path file : javaFiles) {
                transformFile(file);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform source tree: " + sourceRoot, e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        CompilationUnit cu = StaticJavaParser.parse(file);
        boolean changed = false;

        for (ImportDeclaration importDeclaration : new ArrayList<>(cu.getImports())) {
            if (importDeclaration.getNameAsString().equals(OLD_FAST_FRAMED_TRANSPORT)) {
                importDeclaration.setName(NEW_FAST_FRAMED_TRANSPORT);
                changed = true;
            }
        }

        List<ClassOrInterfaceDeclaration> types = new ArrayList<>(cu.findAll(ClassOrInterfaceDeclaration.class));
        types.sort(Comparator.comparingInt(Main::nestingDepth).reversed());

        for (ClassOrInterfaceDeclaration type : types) {
            Set<HelperSignature> neededHelpers = new HashSet<>();

            List<ObjectCreationExpr> creations = new ArrayList<>(type.findAll(ObjectCreationExpr.class));
            for (ObjectCreationExpr creation : creations) {
                if (creation.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null) != type) {
                    continue;
                }
                if (isInsideGeneratedHelper(creation)) {
                    continue;
                }

                String targetType = creation.getType().asString();
                if (isFastFramedTransport(targetType)) {
                    targetType = NEW_FAST_FRAMED_TRANSPORT;
                    creation.setType(StaticJavaParser.parseClassOrInterfaceType(targetType));
                }
                if (!isTargetType(targetType)) {
                    continue;
                }

                int arity = creation.getArguments().size();
                if (!isSupportedArity(targetType, arity)) {
                    continue;
                }

                String helperName = helperName(targetType, arity);
                MethodCallExpr replacement = new MethodCallExpr(helperName);
                for (Expression argument : creation.getArguments()) {
                    replacement.addArgument(argument.clone());
                }

                creation.replace(replacement);
                neededHelpers.add(new HelperSignature(targetType, arity));
                changed = true;
            }

            for (HelperSignature helper : neededHelpers) {
                if (!hasHelper(type, helper)) {
                    type.addMember(StaticJavaParser.parseBodyDeclaration(helperSource(helper)).asMethodDeclaration());
                    changed = true;
                }
            }

            List<TryStmt> tryStmts = new ArrayList<>(type.findAll(TryStmt.class));
            for (TryStmt tryStmt : tryStmts) {
                if (tryStmt.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null) != type) {
                    continue;
                }
                if (tryStmt.getFinallyBlock().isPresent()) {
                    continue;
                }
                if (tryStmt.getCatchClauses().isEmpty()) {
                    continue;
                }
                boolean onlyTransportExceptionCatch = tryStmt.getCatchClauses().stream()
                    .allMatch(Main::isTransportExceptionCatch);
                if (!onlyTransportExceptionCatch) {
                    continue;
                }
                if (containsTargetConstruction(tryStmt.getTryBlock(), type)) {
                    continue;
                }

                BlockStmt replacement = tryStmt.getTryBlock().clone();
                tryStmt.replace(replacement);
                changed = true;
            }
        }

        if (changed) {
            Files.writeString(file, cu.toString());
        }
    }

    private static boolean isTargetType(String typeName) {
        return THRIFT_SERIALIZER.equals(typeName) || typeName.endsWith("TSerializer")
            || THRIFT_DESERIALIZER.equals(typeName) || typeName.endsWith("TDeserializer")
            || THRIFT_FRAMED_TRANSPORT.equals(typeName) || typeName.endsWith("TFramedTransport")
            || THRIFT_IO_STREAM_TRANSPORT.equals(typeName) || typeName.endsWith("TIOStreamTransport")
            || THRIFT_MEMORY_INPUT_TRANSPORT.equals(typeName) || typeName.endsWith("TMemoryInputTransport")
            || THRIFT_MEMORY_BUFFER.equals(typeName) || typeName.endsWith("TMemoryBuffer")
            || NEW_FAST_FRAMED_TRANSPORT.equals(typeName) || typeName.endsWith("TFastFramedTransport");
    }

    private static boolean isSupportedArity(String targetType, int arity) {
        if (targetType.endsWith("TSerializer") || targetType.endsWith("TDeserializer")) {
            return arity == 0 || arity == 1;
        }
        if (targetType.endsWith("TFramedTransport")) {
            return arity == 1 || arity == 2;
        }
        if (targetType.endsWith("TIOStreamTransport")) {
            return arity == 1 || arity == 2 || arity == 3;
        }
        if (targetType.endsWith("TMemoryInputTransport")) {
            return arity == 0 || arity == 1 || arity == 3;
        }
        if (targetType.endsWith("TMemoryBuffer")) {
            return arity == 1;
        }
        if (targetType.endsWith("TFastFramedTransport")) {
            return arity >= 1 && arity <= 3;
        }
        return false;
    }

    private static boolean isFastFramedTransport(String typeName) {
        return OLD_FAST_FRAMED_TRANSPORT.equals(typeName) || typeName.endsWith("TFastFramedTransport");
    }

    private static String helperName(String targetType, int arity) {
        String simpleName = targetType.substring(targetType.lastIndexOf('.') + 1);
        return "thriftConstruct" + simpleName + arity;
    }

    private static boolean hasHelper(ClassOrInterfaceDeclaration type, HelperSignature helper) {
        String name = helperName(helper.targetType, helper.arity);
        return type.getMethodsByName(name).stream().anyMatch(method -> method.getParameters().size() == helper.arity);
    }

    private static boolean isInsideGeneratedHelper(ObjectCreationExpr creation) {
        return creation.findAncestor(MethodDeclaration.class)
            .map(MethodDeclaration::getNameAsString)
            .map(name -> name.startsWith("thriftConstruct"))
            .orElse(false);
    }

    private static boolean isTransportExceptionCatch(CatchClause catchClause) {
        return THRIFT_TRANSPORT_EXCEPTION.equals(catchClause.getParameter().getType().asString());
    }

    private static boolean containsTargetConstruction(BlockStmt block, ClassOrInterfaceDeclaration type) {
        return block.findAll(ObjectCreationExpr.class).stream()
            .anyMatch(creation -> {
                if (creation.findAncestor(ClassOrInterfaceDeclaration.class).orElse(null) != type) {
                    return false;
                }
                return isTargetType(creation.getType().asString());
            });
    }

    private static String helperSource(HelperSignature helper) {
        String helperName = helperName(helper.targetType, helper.arity);
        String simpleName = helper.targetType.substring(helper.targetType.lastIndexOf('.') + 1);
        StringBuilder parameters = new StringBuilder();
        StringBuilder arguments = new StringBuilder();

        if (helper.targetType.endsWith("TSerializer") || helper.targetType.endsWith("TDeserializer")) {
            if (helper.arity == 1) {
                parameters.append(THRIFT_PROTOCOL_FACTORY).append(" p0");
                arguments.append("p0");
            }
        } else if (helper.targetType.endsWith("TFramedTransport") || helper.targetType.endsWith("TFastFramedTransport")) {
            if (helper.arity >= 1) {
                parameters.append(THRIFT_TRANSPORT).append(" p0");
                arguments.append("p0");
            }
            if (helper.arity >= 2) {
                parameters.append(", int p1");
                arguments.append(", p1");
            }
            if (helper.arity >= 3) {
                parameters.append(", int p2");
                arguments.append(", p2");
            }
        } else if (helper.targetType.endsWith("TIOStreamTransport")) {
            if (helper.arity >= 1) {
                parameters.append("java.io.InputStream p0");
                arguments.append("p0");
            }
            if (helper.arity >= 2) {
                parameters.append(", java.io.OutputStream p1");
                arguments.append(", p1");
            }
            if (helper.arity >= 3) {
                parameters.append(", org.apache.thrift.TConfiguration p2");
                arguments.append(", p2");
            }
        } else if (helper.targetType.endsWith("TMemoryInputTransport")) {
            if (helper.arity == 1) {
                parameters.append("byte[] p0");
                arguments.append("p0");
            } else if (helper.arity == 3) {
                parameters.append("org.apache.thrift.TConfiguration p0, byte[] p1, int p2, int p3");
                arguments.append("p0, p1, p2, p3");
            }
        } else if (helper.targetType.endsWith("TMemoryBuffer")) {
            if (helper.arity == 1) {
                parameters.append("int p0");
                arguments.append("p0");
            }
        } else if (helper.targetType.endsWith("TFastFramedTransport")) {
            if (helper.arity >= 1) {
                parameters.append(THRIFT_TRANSPORT).append(" p0");
                arguments.append("p0");
            }
            if (helper.arity >= 2) {
                parameters.append(", int p1");
                arguments.append(", p1");
            }
            if (helper.arity >= 3) {
                parameters.append(", int p2");
                arguments.append(", p2");
            }
        } else if (helper.arity == 1) {
            parameters.append(THRIFT_PROTOCOL_FACTORY).append(" p0");
            arguments.append("p0");
        }

        return "private static " + helper.targetType + " " + helperName + "(" + parameters + ") {"
            + " try { return new " + helper.targetType + "(" + arguments + "); }"
            + " catch (" + THRIFT_TRANSPORT_EXCEPTION + " e) {"
            + " throw new IllegalStateException(\"Failed to instantiate " + simpleName + "\", e); } }";
    }

    private static int nestingDepth(ClassOrInterfaceDeclaration type) {
        int depth = 0;
        var current = type.getParentNode().orElse(null);
        while (current != null) {
            if (current instanceof ClassOrInterfaceDeclaration) {
                depth++;
            }
            current = current.getParentNode().orElse(null);
        }
        return depth;
    }

    private static final class HelperSignature {
        private final String targetType;
        private final int arity;

        private HelperSignature(String targetType, int arity) {
            this.targetType = targetType;
            this.arity = arity;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof HelperSignature)) {
                return false;
            }
            HelperSignature that = (HelperSignature) other;
            return arity == that.arity && targetType.equals(that.targetType);
        }

        @Override
        public int hashCode() {
            return 31 * targetType.hashCode() + arity;
        }
    }
}
