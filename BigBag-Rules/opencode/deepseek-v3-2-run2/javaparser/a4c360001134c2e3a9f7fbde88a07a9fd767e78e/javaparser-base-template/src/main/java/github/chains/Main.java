package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.err.println("Transforms org.reflections.util.FilterBuilder.apply(String) calls to test(String)");
            System.err.println("This fixes the breaking API change in Reflections 0.10.2");
            System.exit(1);
        }
        String sourceDir = args[0];
        System.out.println("Transforming FilterBuilder.apply() to test() in: " + sourceDir);
        System.out.println("This fixes the breaking API change in Reflections 0.10.2");
        System.out.println("FilterBuilder now implements Predicate<String> and uses test() instead of apply()");
        // Set up type solver for symbol resolution
        TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser javaParser = new JavaParser();
        javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
        // Walk through all Java files
        try (Stream<Path> paths = Files.walk(Paths.get(sourceDir))) {
            paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).forEach(filePath -> {
                try {
                    transformFile(filePath.toFile(), javaParser);
                } catch (Exception e) {
                    System.err.println("Error processing " + filePath + ": " + e.getMessage());
                }
            });
        }
        System.out.println("Transformation complete!");
    }

    private static void transformFile(File file, JavaParser javaParser) throws Exception {
        CompilationUnit cu = javaParser.parse(new FileInputStream(file)).getResult().orElseThrow();
        cu.accept(new ModifierVisitor<Void>() {

            @Override
            public Visitable visit(MethodCallExpr n, Void arg) {
                // Check if this is a call to method named "apply"
                if ("apply".equals(n.getName().asString())) {
                    // Look for FilterBuilder.apply(String) pattern
                    // Check if there's exactly one argument
                    if (n.getArguments().size() == 1) {
                        // Heuristic: look for FilterBuilder in the scope expression
                        // This handles both direct calls and chained calls
                        boolean isFilterBuilderCall = false;
                        if (n.getScope().isPresent()) {
                            String scopeCode = n.getScope().map(s -> s.toString()).orElse("");
                            // Match various patterns:
                            // 1. new FilterBuilder()
                            // 2. variable of type FilterBuilder
                            // 3. Method returning FilterBuilder
                            // 4. Field of type FilterBuilder
                            if (scopeCode.contains("FilterBuilder") || scopeCode.matches(".*[Ff]ilter[Bb]uilder.*") || scopeCode.contains("new FilterBuilder(")) {
                                isFilterBuilderCall = true;
                            }
                        }
                        // If we can't determine from scope, check imports
                        if (!isFilterBuilderCall) {
                            // Check if file imports org.reflections.util.FilterBuilder
                            try {
                                CompilationUnit compilationUnit = n.findCompilationUnit().orElse(null);
                                if (compilationUnit != null) {
                                    boolean importsFilterBuilder = compilationUnit.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals("org.reflections.util.FilterBuilder") || imp.getNameAsString().endsWith(".FilterBuilder"));
                                    if (importsFilterBuilder) {
                                        isFilterBuilderCall = true;
                                    }
                                }
                            } catch (Exception e) {
                                // If import check fails, continue with heuristic
                            }
                        }
                        if (isFilterBuilderCall) {
                            n.setName("test");
                            System.out.println("Transformed FilterBuilder.apply() to test() in " + file.getPath());
                        }
                    }
                }
                return super.visit(n, arg);
            }
        }, null);
        // Write the transformed file back
        Files.write(file.toPath(), cu.toString().getBytes());
    }
}
