package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {

    private static final String OLD_TYPE = "Xpp3Dom";
    private static final String OLD_TYPE_FQCN = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_INTERFACE_FQCN = "org.codehaus.plexus.configuration.PlexusConfiguration";
    private static final String NEW_IMPLEMENTATION_FQCN = "org.codehaus.plexus.configuration.DefaultPlexusConfiguration";

    public static void main(String[] args) throws IOException {
        ParserConfiguration configuration = new ParserConfiguration();
        JavaParser parser = new JavaParser(configuration);

        List<Path> roots = new ArrayList<>();
        if (args.length == 0) {
            roots.add(Paths.get("."));
        } else {
            for (String arg : args) {
                roots.add(Paths.get(arg));
            }
        }

        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            Files.walk(root)
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            rewriteFile(parser, path);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to rewrite " + path, e);
                        }
                    });
        }
    }

    private static void rewriteFile(JavaParser parser, Path path) throws IOException {
        CompilationUnit compilationUnit = parser.parse(path).getResult().orElse(null);
        if (compilationUnit == null) {
            return;
        }

        PlexusConfigurationTransformer transformer = new PlexusConfigurationTransformer();
        transformer.visit(compilationUnit, null);

        if (transformer.changed) {
            compilationUnit.getImports().removeIf(importDeclaration ->
                    importDeclaration.getNameAsString().equals(OLD_TYPE_FQCN)
                            || importDeclaration.getNameAsString().equals("org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration"));
            if (compilationUnit.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_INTERFACE_FQCN))) {
                compilationUnit.addImport(NEW_INTERFACE_FQCN);
            }
            if (compilationUnit.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_IMPLEMENTATION_FQCN))) {
                compilationUnit.addImport(NEW_IMPLEMENTATION_FQCN);
            }
            Files.writeString(path, compilationUnit.toString(), StandardCharsets.UTF_8);
        }
    }

    private static final class PlexusConfigurationTransformer extends ModifierVisitor<Void> {

        private boolean changed;

        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            if (n.getType().getNameAsString().equals(OLD_TYPE) || n.getType().getNameAsString().equals("XmlPlexusConfiguration")) {
                changed = true;
                n.getType().setName("DefaultPlexusConfiguration");
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ClassOrInterfaceType n, Void arg) {
            if (n.getNameAsString().equals(OLD_TYPE)) {
                changed = true;
                n.setName("PlexusConfiguration");
            }
            return super.visit(n, arg);
        }
    }
}
