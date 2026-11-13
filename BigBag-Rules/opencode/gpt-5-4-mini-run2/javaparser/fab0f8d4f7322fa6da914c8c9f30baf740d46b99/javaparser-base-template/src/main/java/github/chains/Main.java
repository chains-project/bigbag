package github.chains;

import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.utils.SourceRoot;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    private static final String HEADER_TYPE = "com.artipie.http.Headers.From";

    public static void main(final String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source root path as the first argument");
        }
        final Path sourceRoot = Paths.get(args[0]);
        final SourceRoot root = new SourceRoot(sourceRoot, new ParserConfiguration());
        final List<ParseResult<CompilationUnit>> parsed = root.tryToParse();
        boolean changed = false;
        for (final ParseResult<CompilationUnit> result : parsed) {
            if (result.isSuccessful() && result.getResult().isPresent()) {
                for (final MethodCallExpr call : result.getResult().get().findAll(MethodCallExpr.class)) {
                    changed |= rewrite(call);
                }
            }
        }
        if (changed) {
            root.saveAll();
        }
    }

    private static boolean rewrite(final MethodCallExpr call) {
        if (!"accept".equals(call.getNameAsString()) || call.getArguments().size() != 3) {
            return false;
        }
        final Expression headers = call.getArgument(1);
        if (headers.isObjectCreationExpr()) {
            final ObjectCreationExpr created = headers.asObjectCreationExpr();
            if (created.getType().asString().equals(HEADER_TYPE)) {
                return false;
            }
        }
        final ObjectCreationExpr wrapped = new ObjectCreationExpr(
            null,
            StaticJavaParser.parseClassOrInterfaceType(HEADER_TYPE),
            NodeList.nodeList(headers.clone())
        );
        call.getArguments().set(1, wrapped);
        return true;
    }
}
