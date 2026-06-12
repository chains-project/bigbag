package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.TypeExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class Main {
    private static final String OLD_TYPE = "org.codehaus.plexus.util.xml.Xpp3Dom";
    private static final String NEW_TYPE = "org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration";

    public static void main(String[] args) {
        Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".");
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .forEach(Main::rewriteIfNeeded);
        } catch (IOException e) {
            throw new RuntimeException("Failed to traverse " + sourceRoot, e);
        }
    }

    private static void rewriteIfNeeded(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            String before = cu.toString();
            Xpp3DomToPlexusConfigurationRewriter rewriter = new Xpp3DomToPlexusConfigurationRewriter();
            cu.accept(rewriter, null);
            rewriter.applyCollectedRewrites();
            if (!before.equals(cu.toString())) {
                Files.writeString(path, cu.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to rewrite " + path, e);
        }
    }

    private static final class Xpp3DomToPlexusConfigurationRewriter extends VoidVisitorAdapter<Void> {
        private final List<MethodCallExpr> mavenXpp3ReaderCalls = new ArrayList<>();
        private final Set<ClassOrInterfaceDeclaration> helperOwners = new LinkedHashSet<>();

        @Override
        public void visit(ImportDeclaration n, Void arg) {
            if (OLD_TYPE.equals(n.getNameAsString())) {
                n.setName(NEW_TYPE);
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceType n, Void arg) {
            if ("Xpp3Dom".equals(n.getNameAsString())) {
                n.setName("XmlPlexusConfiguration");
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(CastExpr n, Void arg) {
            if (n.getType() instanceof ClassOrInterfaceType
                && "Xpp3Dom".equals(((ClassOrInterfaceType) n.getType()).getNameAsString())) {
                ((ClassOrInterfaceType) n.getType()).setName("XmlPlexusConfiguration");
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodReferenceExpr n, Void arg) {
            if (n.getScope() instanceof TypeExpr) {
                Node scopeType = ((TypeExpr) n.getScope()).getType();
                if (scopeType instanceof ClassOrInterfaceType
                    && "Xpp3Dom".equals(((ClassOrInterfaceType) scopeType).getNameAsString())) {
                    ((ClassOrInterfaceType) scopeType).setName("XmlPlexusConfiguration");
                }
            }
            super.visit(n, arg);
        }

        @Override
        public void visit(MethodCallExpr n, Void arg) {
            if (isMavenXpp3ReaderRead(n)) {
                mavenXpp3ReaderCalls.add(n);
                n.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(helperOwners::add);
            }
            super.visit(n, arg);
        }

        void applyCollectedRewrites() {
            for (ClassOrInterfaceDeclaration owner : helperOwners) {
                if (owner.getMethodsByName("invokeMavenXpp3ReaderRead").isEmpty()) {
                    owner.addMember(StaticJavaParser.parseBodyDeclaration(
                        "private static org.apache.maven.model.Model invokeMavenXpp3ReaderRead(" +
                            "org.apache.maven.model.io.xpp3.MavenXpp3Reader reader, java.io.Reader source) {" +
                            "  try {" +
                            "    java.lang.reflect.Method method = reader.getClass().getMethod(\"read\", java.io.Reader.class);" +
                            "    return (org.apache.maven.model.Model) method.invoke(reader, source);" +
                            "  } catch (java.lang.reflect.InvocationTargetException e) {" +
                            "    Throwable cause = e.getCause();" +
                            "    if (cause instanceof RuntimeException) { throw (RuntimeException) cause; }" +
                            "    if (cause instanceof Error) { throw (Error) cause; }" +
                            "    throw new RuntimeException(cause);" +
                            "  } catch (ReflectiveOperationException e) {" +
                            "    throw new RuntimeException(e);" +
                            "  }" +
                            "}"));
                }
            }

            for (MethodCallExpr target : mavenXpp3ReaderCalls) {
                MethodCallExpr replacement = new MethodCallExpr();
                replacement.setName("invokeMavenXpp3ReaderRead");
                replacement.addArgument(target.getScope().orElseThrow().clone());
                replacement.addArgument(target.getArgument(0).clone());
                target.replace(replacement);
            }
        }

        private boolean isMavenXpp3ReaderRead(MethodCallExpr n) {
            if (!"read".equals(n.getNameAsString()) || n.getArguments().size() != 1 || !n.getScope().isPresent()) {
                return false;
            }
            if (!(n.getScope().get() instanceof NameExpr)) {
                return false;
            }
            String scopeName = ((NameExpr) n.getScope().get()).getNameAsString();
            return n.findAncestor(ClassOrInterfaceDeclaration.class)
                .map(owner -> owner.findAll(VariableDeclarator.class).stream()
                    .anyMatch(var -> var.getNameAsString().equals(scopeName)
                        && var.getType().asString().endsWith("MavenXpp3Reader")))
                .orElse(false);
        }
    }
}
