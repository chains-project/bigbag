package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {

    private static final String OLD_FQCN = "org.apache.thrift.transport.TFastFramedTransport";
    private static final String NEW_FQCN = "org.apache.thrift.transport.layered.TFastFramedTransport";
    private static final String OLD_SIMPLE = simpleName(OLD_FQCN);
    private static final String NEW_SIMPLE = simpleName(NEW_FQCN);

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]);
        if (!Files.isDirectory(sourceRoot)) {
            throw new IllegalArgumentException("Source root is not a directory: " + sourceRoot);
        }

        JavaParser parser = new JavaParser(new ParserConfiguration());
        Files.walk(sourceRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> transformFile(parser, path));
    }

    private static void transformFile(JavaParser parser, Path path) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(path);
            if (!result.isSuccessful() || !result.getResult().isPresent()) {
                throw new IllegalStateException("Unable to parse " + path + ": " + result.getProblems());
            }

            CompilationUnit cu = result.getResult().get();
            LexicalPreservingPrinter.setup(cu);

            boolean importedOldType = cu.getImports().stream()
                    .map(ImportDeclaration::getNameAsString)
                    .anyMatch(OLD_FQCN::equals);

            boolean changed = rewriteImports(cu);
            changed |= rewriteConstructedTypes(cu, importedOldType);

            if (changed) {
                Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            if (importDeclaration.getNameAsString().equals(OLD_FQCN)) {
                importDeclaration.setName(NEW_FQCN);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteConstructedTypes(CompilationUnit cu, boolean importedOldType) {
        boolean changed = false;

        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            if (type.asString().equals(OLD_FQCN) || (importedOldType && type.getNameAsString().equals(OLD_SIMPLE))) {
                type.replace(com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType(importedOldType ? NEW_SIMPLE : NEW_FQCN));
                changed = true;
            }
        }

        for (ObjectCreationExpr creationExpr : cu.findAll(ObjectCreationExpr.class)) {
            if (creationExpr.getType().asString().equals(OLD_FQCN) || (importedOldType && creationExpr.getType().getNameAsString().equals(OLD_SIMPLE))) {
                creationExpr.setType(com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType(importedOldType ? NEW_SIMPLE : NEW_FQCN));
                changed = true;
                changed |= wrapCheckedExceptionSite(creationExpr);
            }
        }

        return changed;
    }

    private static boolean wrapCheckedExceptionSite(ObjectCreationExpr creationExpr) {
        Optional<Statement> statement = creationExpr.findAncestor(Statement.class);
        Optional<CallableDeclaration> callable = creationExpr.findAncestor(CallableDeclaration.class);
        if (statement.isEmpty() || callable.isEmpty() || callableDeclaresTransportException(callable.get())) {
            return false;
        }

        Statement original = statement.get();
        if (original.toString().contains("catch (org.apache.thrift.transport.TTransportException")) {
            return false;
        }

        String wrapped = "try {\n" + original + "\n} catch (org.apache.thrift.transport.TTransportException e) {\n"
                + "  throw new RuntimeException(e);\n}";
        original.replace(com.github.javaparser.StaticJavaParser.parseStatement(wrapped));
        return true;
    }

    private static boolean callableDeclaresTransportException(CallableDeclaration<?> callable) {
        return callable.getThrownExceptions().stream()
                .map(Object::toString)
                .anyMatch(name -> name.equals("Exception")
                        || name.equals("Throwable")
                        || name.equals("TException")
                        || name.equals("TTransportException")
                        || name.equals("org.apache.thrift.TException")
                        || name.equals("org.apache.thrift.transport.TTransportException"));
    }

    private static String simpleName(String fqcn) {
        int idx = fqcn.lastIndexOf('.');
        return idx >= 0 ? fqcn.substring(idx + 1) : fqcn;
    }
}
