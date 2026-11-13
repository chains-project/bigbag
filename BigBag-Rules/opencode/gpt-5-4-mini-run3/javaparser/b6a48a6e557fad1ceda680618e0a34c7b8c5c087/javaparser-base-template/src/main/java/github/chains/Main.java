package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {
    private static final String FLYWAY_FQCN = "org.flywaydb.core.Flyway";
    private static final Map<String, String> SETTER_TO_FLUENT = new HashMap<>();

    static {
        SETTER_TO_FLUENT.put("setDataSource", "dataSource");
        SETTER_TO_FLUENT.put("setLocations", "locations");
        SETTER_TO_FLUENT.put("setValidateOnMigrate", "validateOnMigrate");
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Paths.get(".") : Paths.get(args[0]);
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java")).forEach(javaFiles::add);
        }

        int changed = 0;
        for (Path file : javaFiles) {
            if (transform(file)) {
                changed++;
            }
        }
        System.out.println("Transformed " + changed + " file(s).");
    }

    private static boolean transform(Path file) throws IOException {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11);
        CompilationUnit cu = StaticJavaParser.parse(file);
        LexicalPreservingPrinter.setup(cu);

        boolean changed = false;
        for (BlockStmt block : cu.findAll(BlockStmt.class)) {
            changed |= rewriteBlock(block);
        }

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
        return changed;
    }

    private static boolean rewriteBlock(BlockStmt block) {
        boolean changed = false;
        List<Statement> statements = new ArrayList<>(block.getStatements());

        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            if (!statement.isExpressionStmt()) {
                continue;
            }

            Expression expression = statement.asExpressionStmt().getExpression();
            if (!expression.isMethodCallExpr()) {
                continue;
            }

            MethodCallExpr call = expression.asMethodCallExpr();
            if (!call.getScope().isPresent() || !call.getScope().get().isNameExpr()) {
                continue;
            }

            String variableName = call.getScope().get().asNameExpr().getNameAsString();
            if (!SETTER_TO_FLUENT.containsKey(call.getNameAsString()) && !"setClassLoader".equals(call.getNameAsString())) {
                continue;
            }

            VariableDeclarator flywayVar = findFlywayVariable(block, variableName);
            if (flywayVar == null) {
                continue;
            }

            List<MethodCallExpr> fluentCalls = new ArrayList<>();
            Expression classLoaderArg = null;
            int j = i;
            while (j < statements.size()) {
                Statement candidateStmt = statements.get(j);
                if (!candidateStmt.isExpressionStmt()) {
                    break;
                }

                Expression candidateExpr = candidateStmt.asExpressionStmt().getExpression();
                if (!candidateExpr.isMethodCallExpr()) {
                    break;
                }

                MethodCallExpr candidateCall = candidateExpr.asMethodCallExpr();
                if (!candidateCall.getScope().isPresent() || !candidateCall.getScope().get().isNameExpr()) {
                    break;
                }
                if (!variableName.equals(candidateCall.getScope().get().asNameExpr().getNameAsString())) {
                    break;
                }

                if ("setClassLoader".equals(candidateCall.getNameAsString())) {
                    if (candidateCall.getArguments().size() == 1) {
                        classLoaderArg = candidateCall.getArgument(0).clone();
                    }
                    block.getStatements().remove(candidateStmt);
                    statements.remove(j);
                    changed = true;
                    continue;
                }

                String fluentName = SETTER_TO_FLUENT.get(candidateCall.getNameAsString());
                if (fluentName == null) {
                    break;
                }

                MethodCallExpr fluentCall = new MethodCallExpr(null, fluentName);
                for (Expression arg : candidateCall.getArguments()) {
                    fluentCall.addArgument(arg.clone());
                }
                fluentCalls.add(fluentCall);
                block.getStatements().remove(candidateStmt);
                statements.remove(j);
                changed = true;
            }

            if (!fluentCalls.isEmpty() || classLoaderArg != null) {
                Expression base = classLoaderArg == null
                        ? new MethodCallExpr(new NameExpr("Flyway"), "configure")
                        : new MethodCallExpr(new NameExpr("Flyway"), "configure").addArgument(classLoaderArg);
                Expression chain = base;
                for (MethodCallExpr fluentCall : fluentCalls) {
                    fluentCall.setScope(chain);
                    chain = fluentCall;
                }
                chain = new MethodCallExpr(chain, "load");

                Expression init = flywayVar.getInitializer().orElse(null);
                if (init != null && init.isObjectCreationExpr()) {
                    flywayVar.setInitializer(chain);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private static VariableDeclarator findFlywayVariable(BlockStmt block, String variableName) {
        for (Statement statement : block.getStatements()) {
            for (VariableDeclarator vd : statement.findAll(VariableDeclarator.class)) {
                if (variableName.equals(vd.getNameAsString()) && isFlywayCreation(vd.getInitializer().orElse(null))) {
                    return vd;
                }
            }
        }
        return null;
    }

    private static boolean isFlywayCreation(Expression expression) {
        if (expression == null || !expression.isObjectCreationExpr()) {
            return false;
        }
        ObjectCreationExpr creation = expression.asObjectCreationExpr();
        return creation.getArguments().isEmpty() && isFlywayType(creation.getTypeAsString());
    }

    private static boolean isFlywayType(String typeName) {
        return "Flyway".equals(typeName) || FLYWAY_FQCN.equals(typeName);
    }
}
