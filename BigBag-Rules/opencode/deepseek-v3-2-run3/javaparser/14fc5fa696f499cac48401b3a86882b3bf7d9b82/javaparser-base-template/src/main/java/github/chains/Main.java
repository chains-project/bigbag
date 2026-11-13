package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        Path sourceDir = Paths.get(args[0]);
        transformFlywayAPI(sourceDir);
    }

    private static void transformFlywayAPI(Path sourceDir) throws IOException {
        Files.walk(sourceDir).filter(p -> p.toString().endsWith(".java")).forEach(file -> {
            try {
                transformFile(file);
            } catch (IOException e) {
                System.err.println("Error processing file: " + file + ": " + e.getMessage());
            }
        });
    }

    private static void transformFile(Path file) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu = parser.parse(file).getResult().orElseThrow();
        // Enable lexical preservation to keep formatting
        LexicalPreservingPrinter.setup(cu);
        // Apply transformation
        FlywayTransformer transformer = new FlywayTransformer();
        cu.accept(transformer, null);
        // Write back the transformed file
        Files.write(file, cu.toString().getBytes());
    }

    private static class FlywayTransformer extends ModifierVisitor<Void> {

        // Map old setter names to new fluent API method names
        private static final Map<String, String> SETTER_TO_FLUENT = new HashMap<>();

        static {
            SETTER_TO_FLUENT.put("setDataSource", "dataSource");
            SETTER_TO_FLUENT.put("setLocations", "locations");
            SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
            // setClassLoader is handled specially - either omitted or passed to configure()
        }

        // Track Flyway variable declarations and their setters
        private final Map<String, List<MethodCallExpr>> variableSetters = new HashMap<>();

        private final Map<String, Expression> variableInitializers = new HashMap<>();

        private final Set<String> flywayVariables = new HashSet<>();

        @Override
        public Visitable visit(VariableDeclarationExpr n, Void arg) {
            // Track Flyway variable declarations
            for (com.github.javaparser.ast.body.VariableDeclarator vd : n.getVariables()) {
                if (vd.getInitializer().isPresent()) {
                    Expression init = vd.getInitializer().get();
                    // Check if this is a Flyway instance
                    if (init instanceof ObjectCreationExpr) {
                        ObjectCreationExpr oce = (ObjectCreationExpr) init;
                        if (oce.getType().asString().equals("Flyway")) {
                            String varName = vd.getNameAsString();
                            flywayVariables.add(varName);
                            variableInitializers.put(varName, oce);
                            variableSetters.put(varName, new ArrayList<>());
                        }
                    } else if (init instanceof MethodCallExpr) {
                        MethodCallExpr mce = (MethodCallExpr) init;
                        // Check if it's Flyway.configure().load()
                        if (mce.getNameAsString().equals("load") && mce.getScope().isPresent() && mce.getScope().get() instanceof MethodCallExpr) {
                            MethodCallExpr configureCall = (MethodCallExpr) mce.getScope().get();
                            if (configureCall.getNameAsString().equals("configure") && configureCall.getScope().isPresent() && configureCall.getScope().get() instanceof NameExpr && ((NameExpr) configureCall.getScope().get()).getNameAsString().equals("Flyway")) {
                                String varName = vd.getNameAsString();
                                flywayVariables.add(varName);
                                variableInitializers.put(varName, mce);
                                variableSetters.put(varName, new ArrayList<>());
                            }
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(MethodCallExpr n, Void arg) {
            // Track setter calls on Flyway variables
            if (n.getScope().isPresent() && n.getNameAsString().startsWith("set")) {
                Expression scope = n.getScope().get();
                if (scope instanceof NameExpr) {
                    String varName = ((NameExpr) scope).getNameAsString();
                    if (flywayVariables.contains(varName)) {
                        variableSetters.computeIfAbsent(varName, k -> new ArrayList<>()).add(n);
                    }
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ReturnStmt n, Void arg) {
            // Transform return statements that return Flyway variables
            if (n.getExpression().isPresent()) {
                Expression expr = n.getExpression().get();
                if (expr instanceof NameExpr) {
                    String varName = ((NameExpr) expr).getNameAsString();
                    if (flywayVariables.contains(varName)) {
                        // Rebuild the Flyway initialization as a fluent chain
                        List<MethodCallExpr> setters = variableSetters.getOrDefault(varName, new ArrayList<>());
                        // Start with Flyway.configure()
                        MethodCallExpr configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
                        MethodCallExpr currentChain = configureCall;
                        // Add fluent method calls for each setter
                        for (MethodCallExpr setter : setters) {
                            String setterName = setter.getNameAsString();
                            String fluentMethod = SETTER_TO_FLUENT.get(setterName);
                            if (fluentMethod != null && !"setClassLoader".equals(setterName)) {
                                // Create the fluent method call
                                NodeList<Expression> args = setter.getArguments();
                                MethodCallExpr fluentCall = new MethodCallExpr(currentChain, fluentMethod);
                                fluentCall.setArguments(args);
                                currentChain = fluentCall;
                            }
                            // Note: setClassLoader is omitted - handled by configure() or not needed
                        }
                        // Add .load() at the end
                        MethodCallExpr loadCall = new MethodCallExpr(currentChain, "load");
                        // Replace the return statement
                        n.setExpression(loadCall);
                        // Mark this variable as processed
                        flywayVariables.remove(varName);
                    }
                }
            }
            return super.visit(n, arg);
        }

        @Override
        public Visitable visit(ExpressionStmt n, Void arg) {
            // Remove setter statements that were incorporated into the fluent chain
            if (n.getExpression() instanceof MethodCallExpr) {
                MethodCallExpr mce = (MethodCallExpr) n.getExpression();
                if (mce.getScope().isPresent() && mce.getNameAsString().startsWith("set")) {
                    Expression scope = mce.getScope().get();
                    if (scope instanceof NameExpr) {
                        String varName = ((NameExpr) scope).getNameAsString();
                        // If this variable was already processed in a return statement, remove this setter
                        if (!flywayVariables.contains(varName) && variableSetters.containsKey(varName)) {
                            // Remove the statement
                            return null;
                        }
                    }
                }
            }
            return super.visit(n, arg);
        }
    }
}
