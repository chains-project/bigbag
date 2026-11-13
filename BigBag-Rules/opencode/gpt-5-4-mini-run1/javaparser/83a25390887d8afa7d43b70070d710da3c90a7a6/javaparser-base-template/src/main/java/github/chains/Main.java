package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.utils.SourceRoot;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source root path argument");
        }

        Path sourceRootPath = Paths.get(args[0]);
        SourceRoot sourceRoot = new SourceRoot(sourceRootPath);
        ParserConfiguration configuration = new ParserConfiguration();
        configuration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        sourceRoot.setParserConfiguration(configuration);

        sourceRoot.tryToParseParallelized();

        OkioBufferCallRewriter rewriter = new OkioBufferCallRewriter();
        for (CompilationUnit cu : sourceRoot.getCompilationUnits()) {
            LexicalPreservingPrinter.setup(cu);
            rewriter.visit(cu, null);
        }

        sourceRoot.saveAll();
    }

    private static final class OkioBufferCallRewriter extends ModifierVisitor<Void> {
        @Override
        public MethodCallExpr visit(MethodCallExpr n, Void arg) {
            MethodCallExpr rewritten = (MethodCallExpr) super.visit(n, arg);
            if (!isOldOkioBufferCall(rewritten)) {
                return rewritten;
            }

            Expression receiver = rewritten.getArgument(0).clone();
            return new MethodCallExpr(receiver, "buffer");
        }

        private boolean isOldOkioBufferCall(MethodCallExpr call) {
            if (!"buffer".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
                return false;
            }

            if (call.getScope().isPresent()) {
                String scopeText = call.getScope().get().toString();
                return "Okio".equals(scopeText) || scopeText.endsWith(".Okio");
            }

            return call.findCompilationUnit()
                    .map(this::hasStaticOkioBufferImport)
                    .orElse(false);
        }

        private boolean hasStaticOkioBufferImport(CompilationUnit cu) {
            return cu.getImports().stream().anyMatch(importDecl ->
                    importDecl.isStatic()
                            && (
                            "okio.Okio.buffer".equals(importDecl.getNameAsString())
                                    || "okio.Okio.*".equals(importDecl.getNameAsString())
                                    || importDecl.getNameAsString().endsWith(".Okio.buffer")
                                    || importDecl.getNameAsString().endsWith(".Okio.*")
                    ));
        }
    }
}
