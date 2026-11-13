package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String DATE_TIME = "org.joda.time.DateTime";
    private static final String DATE_MIDNIGHT = "org.joda.time.DateMidnight";
    private static final Set<String> CALENDAR_TYPES = new HashSet<String>();

    static {
        CALENDAR_TYPES.add("java.util.Calendar");
        CALENDAR_TYPES.add("java.util.GregorianCalendar");
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-root>");
        }

        Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        for (Path root : discoverSourceRoots(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }

        ParserConfiguration config = new ParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
        JavaParser parser = new JavaParser(config);

        List<Path> javaFiles = new ArrayList<Path>();
        Files.walk(sourceRoot)
                .filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
                .forEach(javaFiles::add);

        for (Path file : javaFiles) {
            CompilationUnit cu = parser.parse(file).getResult().orElse(null);
            if (cu == null) {
                continue;
            }

            LexicalPreservingPrinter.setup(cu);
            boolean[] changed = new boolean[] { false };

            cu.findAll(ObjectCreationExpr.class).forEach(creation -> {
                if (!isJodaInstantCreation(creation)) {
                    return;
                }
                if (creation.getArguments().size() != 1) {
                    return;
                }

                Expression argument = creation.getArgument(0);
                if (!isCalendarExpression(argument)) {
                    return;
                }

                if (argument.isMethodCallExpr() && "getTimeInMillis".equals(argument.asMethodCallExpr().getNameAsString())) {
                    return;
                }

                creation.setArgument(0, wrapGetTimeInMillis(argument));
                changed[0] = true;
            });

            if (changed[0]) {
                Files.write(file, LexicalPreservingPrinter.print(cu).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
    }

    private static List<Path> discoverSourceRoots(Path root) throws IOException {
        List<Path> roots = new ArrayList<Path>();
        Files.walk(root)
                .filter(p -> Files.isDirectory(p) && (p.endsWith(Paths.get("src/main/java")) || p.endsWith(Paths.get("src/test/java"))))
                .forEach(roots::add);
        if (roots.isEmpty()) {
            roots.add(root);
        }
        return roots;
    }

    private static boolean isJodaInstantCreation(ObjectCreationExpr creation) {
        String typeName = resolvedQualifiedName(creation.getType());
        if (DATE_TIME.equals(typeName) || DATE_MIDNIGHT.equals(typeName)) {
            return true;
        }
        String simple = creation.getType().getNameAsString();
        return "DateTime".equals(simple) || "DateMidnight".equals(simple);
    }

    private static boolean isCalendarExpression(Expression expression) {
        try {
            ResolvedType type = expression.calculateResolvedType();
            if (type.isReferenceType()) {
                ResolvedReferenceType ref = type.asReferenceType();
                if (CALENDAR_TYPES.contains(ref.getQualifiedName())) {
                    return true;
                }
                for (ResolvedReferenceType ancestor : ref.getAllAncestors()) {
                    if (CALENDAR_TYPES.contains(ancestor.getQualifiedName())) {
                        return true;
                    }
                }
            }
        } catch (UnsolvedSymbolException ignored) {
            // Fall back to syntax-based matching.
        } catch (RuntimeException ignored) {
            // Fall back to syntax-based matching.
        }

        if (expression.isMethodCallExpr()) {
            MethodCallExpr call = expression.asMethodCallExpr();
            return "getCalendar".equals(call.getNameAsString()) || "toGregorianCalendar".equals(call.getNameAsString());
        }

        if (expression.isNameExpr()) {
            return looksLikeCalendarName(expression.asNameExpr());
        }

        return false;
    }

    private static boolean looksLikeCalendarName(NameExpr nameExpr) {
        try {
            ResolvedValueDeclaration resolved = nameExpr.resolve();
            return CALENDAR_TYPES.contains(resolved.getType().describe());
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static Expression wrapGetTimeInMillis(Expression expression) {
        MethodCallExpr call = new MethodCallExpr(expression.clone(), "getTimeInMillis");
        return call;
    }

    private static String resolvedQualifiedName(ClassOrInterfaceType type) {
        return type.getNameAsString();
    }
}
