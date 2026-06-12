package github.chains;

import com.github.javaparser.*;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import com.github.javaparser.printer.*;
import com.github.javaparser.printer.configuration.*;
import com.github.javaparser.resolution.*;
import com.github.javaparser.symbolsolver.*;
import com.github.javaparser.symbolsolver.resolution.typesolvers.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Applying Flyway API migration transformation to: " + sourceDir);
        
        try {
            transformFlywayApi(sourceDir);
            System.out.println("Transformation completed successfully");
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static void transformFlywayApi(String sourceDir) throws IOException {
        Path sourcePath = Paths.get(sourceDir);
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            throw new IllegalArgumentException("Source directory does not exist: " + sourceDir);
        }
        
        JavaParser parser = new JavaParser(new ParserConfiguration()
            .setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver())));
        
        List<Path> javaFiles = Files.walk(sourcePath)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
        
        int transformedFiles = 0;
        
        for (Path javaFile : javaFiles) {
            ParseResult<CompilationUnit> parseResult = parser.parse(javaFile);
            if (!parseResult.isSuccessful() || !parseResult.getResult().isPresent()) {
                System.err.println("Failed to parse: " + javaFile);
                continue;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            boolean modified = transformCompilationUnit(cu);
            
            if (modified) {
                saveCompilationUnit(cu, javaFile);
                transformedFiles++;
                System.out.println("Transformed: " + javaFile);
            }
        }
        
        System.out.println("Total files transformed: " + transformedFiles);
    }
    
    private static boolean transformCompilationUnit(CompilationUnit cu) {
        boolean modified = false;
        
        List<ObjectCreationExpr> flywayCreations = cu.findAll(ObjectCreationExpr.class, expr -> {
            Type type = expr.getType();
            String typeName = type != null ? type.asString() : "";
            return typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway");
        });
        
        for (ObjectCreationExpr creation : flywayCreations) {
            if (creation.getArguments().isEmpty()) {
                System.out.println("Found Flyway creation with no args at line: " + 
                    creation.getRange().map(r -> r.begin.line).orElse(-1));
                modified |= transformFlywayCreation(creation);
            }
        }
        
        return modified;
    }
    
    private static boolean transformFlywayCreation(ObjectCreationExpr creation) {
        try {
            // Get the parent statement to find all setter calls
            Statement parentStatement = findParentStatement(creation);
            if (parentStatement == null) {
                return false;
            }
            
            // Find variable name
            String variableName = findVariableName(creation);
            if (variableName == null) {
                return false;
            }
            
            // Find all setter calls on this variable after the creation
            List<MethodCallExpr> setterCalls = new ArrayList<>();
            BlockStmt containingBlock = findContainingBlock(parentStatement);
            if (containingBlock == null) {
                return false;
            }
            
            // Find the index of parent statement in the block
            int statementIndex = -1;
            List<Node> blockStatements = containingBlock.getChildNodes();
            for (int i = 0; i < blockStatements.size(); i++) {
                if (blockStatements.get(i) == parentStatement) {
                    statementIndex = i;
                    break;
                }
            }
            
            if (statementIndex == -1) {
                return false;
            }
            
            // Collect setter calls that follow
            for (int i = statementIndex + 1; i < blockStatements.size(); i++) {
                Node stmt = blockStatements.get(i);
                List<MethodCallExpr> callsInStmt = stmt.findAll(MethodCallExpr.class, call -> {
                    Expression scope = call.getScope().orElse(null);
                    return scope != null && scope.toString().equals(variableName) && 
                           call.getNameAsString().startsWith("set");
                });
                setterCalls.addAll(callsInStmt);
            }
            
            if (setterCalls.isEmpty()) {
                return false;
            }
            
            // Build the new fluent API call
            MethodCallExpr configureCall = new MethodCallExpr(new NameExpr("Flyway"), "configure");
            MethodCallExpr fluentChain = configureCall;
            
            // Map setter names to their arguments
            Map<String, Expression> setters = new HashMap<>();
            for (MethodCallExpr setter : setterCalls) {
                String setName = setter.getNameAsString();
                List<Expression> args = setter.getArguments();
                if (!args.isEmpty()) {
                    setters.put(setName, args.get(0));
                }
            }
            
            // Apply setters in order: dataSource, classLoader, locations, validateOnMigrate
            if (setters.containsKey("setDataSource")) {
                fluentChain = new MethodCallExpr(fluentChain, "dataSource", 
                    new NodeList<>(setters.get("setDataSource")));
            }
            
            if (setters.containsKey("setClassLoader")) {
                fluentChain = new MethodCallExpr(fluentChain, "classLoader", 
                    new NodeList<>(setters.get("setClassLoader")));
            }
            
            if (setters.containsKey("setLocations")) {
                fluentChain = new MethodCallExpr(fluentChain, "locations", 
                    new NodeList<>(setters.get("setLocations")));
            }
            
            if (setters.containsKey("setValidateOnMigrate")) {
                fluentChain = new MethodCallExpr(fluentChain, "validateOnMigrate", 
                    new NodeList<>(setters.get("setValidateOnMigrate")));
            }
            
            MethodCallExpr loadCall = new MethodCallExpr(fluentChain, "load", new NodeList<>());
            
            // Replace the Flyway creation with the new fluent API call
            creation.replace(loadCall);
            
            // Remove the setter statements
            for (MethodCallExpr setter : setterCalls) {
                Statement setterStmt = findParentStatement(setter);
                if (setterStmt != null) {
                    setterStmt.remove();
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Error transforming Flyway creation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private static Statement findParentStatement(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof Statement) {
                return (Statement) current;
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }
    
    private static BlockStmt findContainingBlock(Statement statement) {
        Node current = statement;
        while (current != null) {
            if (current instanceof BlockStmt) {
                return (BlockStmt) current;
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }
    
    private static String findVariableName(ObjectCreationExpr creation) {
        Node parent = creation.getParentNode().orElse(null);
        if (parent instanceof VariableDeclarationExpr) {
            VariableDeclarationExpr varDecl = (VariableDeclarationExpr) parent;
            if (!varDecl.getVariables().isEmpty()) {
                return varDecl.getVariables().get(0).getNameAsString();
            }
        } else if (parent instanceof AssignExpr) {
            AssignExpr assign = (AssignExpr) parent;
            return assign.getTarget().toString();
        } else if (parent instanceof ExpressionStmt) {
            Expression expr = ((ExpressionStmt) parent).getExpression();
            if (expr instanceof AssignExpr) {
                return ((AssignExpr) expr).getTarget().toString();
            }
        }
        return null;
    }
    
    private static void saveCompilationUnit(CompilationUnit cu, Path file) throws IOException {
        Printer printer = new DefaultPrettyPrinter();
        String content = printer.print(cu);
        Files.write(file, content.getBytes());
    }
}