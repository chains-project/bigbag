package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {

    private static final String OLD_BRANCH = "de.gwdg.metadataqa.api.json.JsonBranch";
    private static final String NEW_BRANCH = "de.gwdg.metadataqa.api.json.DataElement";
    private static final String OLD_ACCESSOR = "getJsonPath";
    private static final String NEW_ACCESSOR = "getPath";

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: Main <source-directory>");
            System.exit(1);
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("Not a directory: " + root);
            System.exit(1);
        }

        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);

        try {
            Files.walk(root)
                .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                .forEach(Main::transformFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void transformFile(Path file) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(file);
            LexicalPreservingPrinter.setup(cu);
            BranchTransformer transformer = new BranchTransformer();
            transformer.visit(cu, null);
            if (transformer.changed) {
                Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static final class BranchTransformer extends ModifierVisitor<Void> {
        private boolean changed;

        @Override
        public com.github.javaparser.ast.Node visit(ImportDeclaration n, Void arg) {
            if (n.getNameAsString().equals(OLD_BRANCH)) {
                changed = true;
                n.setName(NEW_BRANCH);
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            if (n.getNameAsString().equals("JsonBranch")) {
                changed = true;
                n.setName("DataElement");
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(Parameter n, Void arg) {
            if (n.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("JsonBranch")) {
                    changed = true;
                    type.setName("DataElement");
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(VariableDeclarator n, Void arg) {
            if (n.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = n.getType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("JsonBranch")) {
                    changed = true;
                    type.setName("DataElement");
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            if (n.getElementType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = n.getElementType().asClassOrInterfaceType();
                if (type.getNameAsString().equals("JsonBranch")) {
                    changed = true;
                    type.setName("DataElement");
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            if (n.getNameAsString().equals(OLD_ACCESSOR)) {
                changed = true;
                n.setName(NEW_ACCESSOR);
            }
            return super.visit(n, arg);
        }

    }
}
