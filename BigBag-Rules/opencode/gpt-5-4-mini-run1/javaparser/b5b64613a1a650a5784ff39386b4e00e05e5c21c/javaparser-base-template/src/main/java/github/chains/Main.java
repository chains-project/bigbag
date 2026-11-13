package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Main {
    private static final String CLIENT_HELPER = "org.jenkinsci.plugins.p4.client.ClientHelper";
    private static final String PERFORCE_SCM = "org.jenkinsci.plugins.p4.PerforceScm";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        CombinedTypeSolver solver = new CombinedTypeSolver(
                new ReflectionTypeSolver(),
                new JavaParserTypeSolver(sourceRoot)
        );
        JavaParser parser = new JavaParser(new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(solver)));

        List<Path> files;
        try (var stream = Files.walk(sourceRoot)) {
            files = stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                    .sorted(Comparator.naturalOrder())
                    .collect(Collectors.toList());
        }

        for (Path file : files) {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (result.getResult().isEmpty()) {
                continue;
            }
            CompilationUnit cu = result.getResult().get();
            boolean changed = rewriteStringUtils(cu);
            changed |= rewriteClientHelper(cu);
            if (changed) {
                Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
            }
        }
    }

    private static boolean rewriteStringUtils(CompilationUnit cu) {
        boolean changed = false;
        List<MethodCallExpr> calls = new ArrayList<>(cu.findAll(MethodCallExpr.class));
        for (MethodCallExpr call : calls) {
            if (!"isAllBlank".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
                continue;
            }
            Expression replacement = null;
            for (Expression arg : call.getArguments()) {
                MethodCallExpr blank = new MethodCallExpr(new NameExpr("StringUtils"), "isBlank", NodeList.nodeList(arg.clone()));
                replacement = replacement == null ? blank : new BinaryExpr(replacement, blank, BinaryExpr.Operator.AND);
            }
            if (replacement != null) {
                call.replace(replacement);
                changed = true;
            }
        }

        if (changed) {
            cu.getImports().removeIf(i -> i.isStatic() && i.toString().contains("org.apache.commons.lang3.StringUtils.isAllBlank"));
            if (cu.findAll(MethodCallExpr.class).stream().anyMatch(c -> "isBlank".equals(c.getNameAsString()))) {
                boolean hasImport = cu.getImports().stream()
                        .anyMatch(i -> !i.isStatic() && !i.isAsterisk() && i.getNameAsString().equals("org.apache.commons.lang3.StringUtils"));
                if (!hasImport) {
                    cu.addImport("org.apache.commons.lang3.StringUtils");
                }
            }
        }
        return changed;
    }

    private static boolean rewriteClientHelper(CompilationUnit cu) {
        boolean changed = false;
        for (ObjectCreationExpr creation : new ArrayList<>(cu.findAll(ObjectCreationExpr.class))) {
            if (!isTargetClientHelper(creation) || creation.getArguments().size() != 4) {
                continue;
            }

            Expression context = inferContext(creation).orElse(null);
            Expression workspace = inferWorkspace(creation).orElse(null);
            if (context == null || workspace == null) {
                continue;
            }

            NodeList<Expression> args = new NodeList<>();
            args.add(context);
            args.add(creation.getArgument(0).clone());
            args.add(creation.getArgument(1).clone());
            args.add(workspace);
            creation.setArguments(args);
            changed = true;
        }
        return changed;
    }

    private static boolean isTargetClientHelper(ObjectCreationExpr creation) {
        if (!"ClientHelper".equals(creation.getType().getNameAsString())) {
            return false;
        }
        try {
            ResolvedConstructorDeclaration resolved = creation.resolve();
            return CLIENT_HELPER.equals(resolved.declaringType().getQualifiedName());
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static Optional<Expression> inferContext(ObjectCreationExpr creation) {
        Optional<Expression> direct = nearbyExpression(creation, Main::isContextType);
        if (direct.isPresent()) {
            return direct.map(Main::normalizeContext);
        }
        return nearbyName(creation, List.of("build", "project", "context", "run"))
                .map(NameExpr::new)
                .map(Main::normalizeContext);
    }

    private static Optional<Expression> inferWorkspace(ObjectCreationExpr creation) {
        Optional<Expression> direct = nearbyExpression(creation, t -> P4WorkspaceType(t));
        if (direct.isPresent()) {
            return direct;
        }
        Optional<Expression> scm = nearbyExpression(creation, t -> PERFORCE_SCM.equals(t.describe()));
        if (scm.isPresent()) {
            return Optional.of(new MethodCallExpr(scm.get(), "getWorkspace"));
        }
        Optional<String> name = nearbyName(creation, List.of("perforceScm", "scm", "p4"));
        return name.map(NameExpr::new).map(expr -> (Expression) new MethodCallExpr(expr, "getWorkspace"));
    }

    private static Optional<String> nearbyName(ObjectCreationExpr creation, List<String> preferred) {
        int line = creation.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE);
        return creation.findCompilationUnit()
                .flatMap(cu -> cu.findAll(NameExpr.class).stream()
                        .filter(n -> n.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE) <= line)
                        .filter(n -> preferred.contains(n.getNameAsString()))
                        .sorted((a, b) -> Integer.compare(
                                b.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE),
                                a.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE)))
                        .map(NameExpr::getNameAsString)
                        .findFirst());
    }

    private static Optional<Expression> nearbyExpression(ObjectCreationExpr creation, java.util.function.Predicate<ResolvedType> predicate) {
        int line = creation.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE);
        return creation.findCompilationUnit()
                .flatMap(cu -> cu.findAll(Expression.class).stream()
                        .filter(expr -> !expr.equals(creation))
                        .filter(expr -> expr.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE) <= line)
                        .sorted((a, b) -> Integer.compare(
                                b.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE),
                                a.getBegin().map(p -> p.line).orElse(Integer.MAX_VALUE)))
                        .filter(expr -> resolvesTo(expr, predicate))
                        .map(Expression::clone)
                        .findFirst());
    }

    private static boolean resolvesTo(Expression expr, java.util.function.Predicate<ResolvedType> predicate) {
        try {
            return predicate.test(expr.calculateResolvedType());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static Expression normalizeContext(Expression expr) {
        if (expr.isNameExpr()) {
            String name = expr.asNameExpr().getNameAsString();
            if (name.equals("build")) {
                return new MethodCallExpr(expr, "getProject");
            }
            if (name.equals("run")) {
                return new MethodCallExpr(expr, "getParent");
            }
        }
        if (isType(expr, "hudson.model.AbstractBuild") || isType(expr, "hudson.model.Run")) {
            return new MethodCallExpr(expr, "getProject");
        }
        return expr;
    }

    private static boolean isContextType(ResolvedType type) {
        String desc = type.describe();
        return desc.equals("hudson.model.Item")
                || desc.equals("hudson.model.ItemGroup")
                || desc.equals("hudson.model.Job")
                || desc.equals("hudson.model.Run")
                || desc.equals("hudson.model.AbstractBuild")
                || desc.equals("hudson.model.AbstractProject")
                || (type.isReferenceType() && type.asReferenceType().getAllAncestors().stream().map(a -> a.getQualifiedName())
                .anyMatch(q -> q.equals("hudson.model.Item") || q.equals("hudson.model.ItemGroup") || q.equals("hudson.model.Job") || q.equals("hudson.model.Run") || q.equals("hudson.model.AbstractBuild") || q.equals("hudson.model.AbstractProject")));
    }

    private static boolean P4WorkspaceType(ResolvedType type) {
        return type.describe().equals("org.jenkinsci.plugins.p4.workspace.Workspace");
    }

    private static boolean isType(Expression expr, String fqcn) {
        try {
            return expr.calculateResolvedType().describe().equals(fqcn);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
