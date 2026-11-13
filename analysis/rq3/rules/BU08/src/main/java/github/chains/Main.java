package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class Main {

    private static final String OLD_TLS_FATAL_ALERT = "org.bouncycastle.crypto.tls.TlsFatalAlert";
    private static final String OLD_ALERT_DESCRIPTION = "org.bouncycastle.crypto.tls.AlertDescription";
    private static final String NEW_IO_EXCEPTION = "java.io.IOException";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path");
        }

        Path sourceRootPath = Paths.get(args[0]);
        SourceRoot sourceRoot = new SourceRoot(sourceRootPath, new ParserConfiguration());
        sourceRoot.tryToParseParallelized();

        for (CompilationUnit cu : sourceRoot.getCompilationUnits()) {
            transformCompilationUnit(cu);
        }

        for (CompilationUnit cu : sourceRoot.getCompilationUnits()) {
            cu.getStorage().ifPresent(storage -> {
                try {
                    Files.writeString(storage.getPath(), cu.toString(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean changed = removeObsoleteImports(cu);
        changed |= rewriteRemovedAlertDescriptionReferences(cu);

        for (TypeDeclaration<?> type : cu.getTypes()) {
            changed |= type.findAll(ThrowStmt.class).stream().anyMatch(stmt -> rewriteThrow(stmt, cu));
        }

        return changed;
    }

    private static boolean removeObsoleteImports(CompilationUnit cu) {
        boolean removed = false;
        removed |= cu.getImports().removeIf(importDecl -> OLD_TLS_FATAL_ALERT.equals(importDecl.getNameAsString())
            || OLD_ALERT_DESCRIPTION.equals(importDecl.getNameAsString()));
        if (removed) {
            ensureImport(cu, IOException.class);
        }
        return removed;
    }

    private static boolean rewriteRemovedAlertDescriptionReferences(CompilationUnit cu) {
        boolean changed = false;
        for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
            if (isRemovedAlertDescriptionReference(fieldAccessExpr)) {
                fieldAccessExpr.replace(new StringLiteralExpr(fieldAccessExpr.getNameAsString()));
                changed = true;
            }
        }
        return changed;
    }

    private static void ensureImport(CompilationUnit cu, Class<?> type) {
        if (!cu.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(type.getName()))) {
            cu.addImport(type);
        }
    }

    private static boolean rewriteThrow(ThrowStmt throwStmt, CompilationUnit cu) {
        Optional<ObjectCreationExpr> maybeCreation = throwStmt.getExpression().toObjectCreationExpr();
        if (maybeCreation.isEmpty()) {
            return false;
        }

        ObjectCreationExpr creation = maybeCreation.get();
        if (!isOldTlsFatalAlert(creation)) {
            return false;
        }

        Expression replacementArgument = creation.getArguments().isEmpty()
            ? new StringLiteralExpr("bad_record_mac")
            : toIOExceptionArgument(creation.getArgument(0));

        ObjectCreationExpr replacement = new ObjectCreationExpr(null, com.github.javaparser.StaticJavaParser.parseClassOrInterfaceType(NEW_IO_EXCEPTION), new NodeList<>(replacementArgument));
        throwStmt.setExpression(replacement);
        ensureImport(cu, IOException.class);
        return true;
    }

    private static boolean isOldTlsFatalAlert(ObjectCreationExpr creation) {
        return creation.getType().getNameAsString().equals("TlsFatalAlert");
    }

    private static boolean isRemovedAlertDescriptionReference(FieldAccessExpr fieldAccessExpr) {
        return fieldAccessExpr.getScope().isNameExpr()
            && fieldAccessExpr.getScope().asNameExpr().getNameAsString().equals("AlertDescription");
    }

    private static Expression toIOExceptionArgument(Expression oldArgument) {
        if (oldArgument.isFieldAccessExpr()) {
            FieldAccessExpr fieldAccessExpr = oldArgument.asFieldAccessExpr();
            if (fieldAccessExpr.getScope().isNameExpr() && OLD_ALERT_DESCRIPTION.equals(fieldAccessExpr.getScope().asNameExpr().getNameAsString())) {
                return new StringLiteralExpr(fieldAccessExpr.getNameAsString());
            }
        }
        if (oldArgument.isNameExpr()) {
            NameExpr nameExpr = oldArgument.asNameExpr();
            if (nameExpr.getNameAsString().equals("bad_record_mac")) {
                return new StringLiteralExpr(nameExpr.getNameAsString());
            }
        }
        return oldArgument.clone();
    }
}
