package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("Transforms Flyway 8.x code to Flyway 9.x API");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = Files.walk(Paths.get(sourceDir))
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (transformFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static boolean transformFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        
        try {
            cu = parser.parse(filePath).getResult().orElse(null);
        } catch (Exception e) {
            System.err.println("Error parsing " + filePath + ": " + e.getMessage());
            return false;
        }
        
        if (cu == null) {
            return false;
        }
        
        FlywayMigrationVisitor visitor = new FlywayMigrationVisitor();
        cu.accept(visitor, null);
        
        if (visitor.wasModified()) {
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            String newContent = printer.print(cu);
            Files.write(filePath, newContent.getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    static class FlywayMigrationVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        @Override
        public Visitable visit(MethodDeclaration n, Void arg) {
            // Look for methods that create and configure Flyway instances
            BlockStmt body = n.getBody().orElse(null);
            if (body == null) {
                return super.visit(n, arg);
            }
            
            // Find patterns like:
            // Flyway flyway = new Flyway();
            // flyway.setXxx(...);
            // flyway.setYyy(...);
            // return flyway;
            
            List<Statement> statements = body.getStatements();
            Map<String, FlywayCreationInfo> flywayCreations = new HashMap<>();
            
            // First pass: Find Flyway variable declarations
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof VariableDeclarationExpr) {
                        VariableDeclarationExpr varDecl = (VariableDeclarationExpr) exprStmt.getExpression();
                        for (VariableDeclarator var : varDecl.getVariables()) {
                            String typeName = var.getType().asString();
                            if ((typeName.equals("Flyway") || typeName.equals("org.flywaydb.core.Flyway")) && 
                                var.getInitializer().isPresent() &&
                                var.getInitializer().get() instanceof ObjectCreationExpr) {
                                
                                ObjectCreationExpr creation = (ObjectCreationExpr) var.getInitializer().get();
                                if (creation.getType().asString().equals("Flyway") && 
                                    creation.getArguments().isEmpty()) {
                                    
                                    String varName = var.getNameAsString();
                                    flywayCreations.put(varName, new FlywayCreationInfo(varName, i));
                                }
                            }
                        }
                    }
                }
            }
            
            if (flywayCreations.isEmpty()) {
                return super.visit(n, arg);
            }
            
            // Second pass: Find setter calls on Flyway variables
            for (int i = 0; i < statements.size(); i++) {
                Statement stmt = statements.get(i);
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof MethodCallExpr) {
                        MethodCallExpr methodCall = (MethodCallExpr) exprStmt.getExpression();
                        Optional<Expression> scope = methodCall.getScope();
                        if (scope.isPresent() && scope.get() instanceof NameExpr) {
                            String varName = ((NameExpr) scope.get()).getNameAsString();
                            FlywayCreationInfo info = flywayCreations.get(varName);
                            if (info != null && isFlywaySetter(methodCall)) {
                                info.addSetterCall(methodCall, i);
                            }
                        }
                    }
                } else if (stmt instanceof ReturnStmt) {
                    ReturnStmt returnStmt = (ReturnStmt) stmt;
                    Optional<Expression> returnExpr = returnStmt.getExpression();
                    if (returnExpr.isPresent() && returnExpr.get() instanceof NameExpr) {
                        String varName = ((NameExpr) returnExpr.get()).getNameAsString();
                        FlywayCreationInfo info = flywayCreations.get(varName);
                        if (info != null) {
                            info.setReturnStmt(returnStmt, i);
                        }
                    }
                }
            }
            
            // Third pass: Apply transformations
            for (FlywayCreationInfo info : flywayCreations.values()) {
                if (info.hasSetterCalls() && info.hasReturnStmt()) {
                    // Special handling for setClassLoader - it should be passed to configure()
                    Expression classLoaderArg = null;
                    List<MethodCallExpr> otherSetters = new ArrayList<>();
                    
                    for (MethodCallExpr setter : info.getSetterCalls()) {
                        if (setter.getNameAsString().equals("setClassLoader")) {
                            // setClassLoader should be passed to configure() as argument
                            classLoaderArg = setter.getArguments().get(0);
                        } else {
                            otherSetters.add(setter);
                        }
                    }
                    
                    // Build the fluent API replacement
                    MethodCallExpr configureCall;
                    if (classLoaderArg != null) {
                        configureCall = new MethodCallExpr(
                            new NameExpr("Flyway"), 
                            "configure",
                            new NodeList<>(classLoaderArg)
                        );
                    } else {
                        configureCall = new MethodCallExpr(
                            new NameExpr("Flyway"), 
                            "configure",
                            new NodeList<>()
                        );
                    }
                    
                    // Chain all the other setter calls as fluent method calls
                    Expression currentExpr = configureCall;
                    for (MethodCallExpr setter : otherSetters) {
                        String setterName = setter.getNameAsString();
                        String fluentName = setterName.substring(3); // Remove "set"
                        fluentName = Character.toLowerCase(fluentName.charAt(0)) + fluentName.substring(1);
                        
                        // Handle special case: setValidateOnMigrate -> validateOnMigrate
                        if (fluentName.equals("validateOnMigrate")) {
                            // Already correct
                        }
                        
                        MethodCallExpr fluentCall = new MethodCallExpr(
                            currentExpr,
                            fluentName,
                            setter.getArguments()
                        );
                        currentExpr = fluentCall;
                    }
                    
                    // Add .load() at the end
                    MethodCallExpr loadCall = new MethodCallExpr(currentExpr, "load", new NodeList<>());
                    
                    // Replace the return statement
                    info.getReturnStmt().setExpression(loadCall);
                    
                    // Remove the variable declaration and setter calls
                    List<Integer> indicesToRemove = new ArrayList<>();
                    indicesToRemove.add(info.getCreationIndex());
                    indicesToRemove.addAll(info.getSetterIndices());
                    
                    // Remove in reverse order to maintain indices
                    indicesToRemove.sort(Collections.reverseOrder());
                    for (int index : indicesToRemove) {
                        body.getStatements().remove(index);
                    }
                    
                    modified = true;
                }
            }
            
            return super.visit(n, arg);
        }
        
        private boolean isFlywaySetter(MethodCallExpr expr) {
            String methodName = expr.getNameAsString();
            return methodName.startsWith("set") && (
                methodName.equals("setDataSource") ||
                methodName.equals("setClassLoader") ||
                methodName.equals("setLocations") ||
                methodName.equals("setValidateOnMigrate") ||
                methodName.equals("setBaselineVersion") ||
                methodName.equals("setBaselineDescription") ||
                methodName.equals("setBaselineOnMigrate") ||
                methodName.equals("setPlaceholders") ||
                methodName.equals("setPlaceholderPrefix") ||
                methodName.equals("setPlaceholderSuffix") ||
                methodName.equals("setSqlMigrationPrefix") ||
                methodName.equals("setRepeatableSqlMigrationPrefix") ||
                methodName.equals("setSqlMigrationSeparator") ||
                methodName.equals("setSqlMigrationSuffixes") ||
                methodName.equals("setEncoding") ||
                methodName.equals("setSchemas") ||
                methodName.equals("setTable") ||
                methodName.equals("setTarget") ||
                methodName.equals("setOutOfOrder") ||
                methodName.equals("setIgnoreMissingMigrations") ||
                methodName.equals("setIgnoreIgnoredMigrations") ||
                methodName.equals("setIgnorePendingMigrations") ||
                methodName.equals("setIgnoreFutureMigrations") ||
                methodName.equals("setCleanOnValidationError") ||
                methodName.equals("setCleanDisabled") ||
                methodName.equals("setMixed") ||
                methodName.equals("setGroup") ||
                methodName.equals("setInstalledBy") ||
                methodName.equals("setErrorOverrides") ||
                methodName.equals("setDryRunOutput") ||
                methodName.equals("setStream") ||
                methodName.equals("setBatch") ||
                methodName.equals("setOracleSqlplus") ||
                methodName.equals("setOracleSqlplusWarn") ||
                methodName.equals("setKerberosConfigFile") ||
                methodName.equals("setOracleWalletLocation") ||
                methodName.equals("setLicenseKey") ||
                methodName.equals("setFailOnMissingLocations")
            );
        }
        
        public boolean wasModified() {
            return modified;
        }
    }
    
    static class FlywayCreationInfo {
        private final String varName;
        private final int creationIndex;
        private final List<MethodCallExpr> setterCalls = new ArrayList<>();
        private final List<Integer> setterIndices = new ArrayList<>();
        private ReturnStmt returnStmt;
        private int returnIndex;
        
        FlywayCreationInfo(String varName, int creationIndex) {
            this.varName = varName;
            this.creationIndex = creationIndex;
        }
        
        void addSetterCall(MethodCallExpr setter, int index) {
            setterCalls.add(setter);
            setterIndices.add(index);
        }
        
        void setReturnStmt(ReturnStmt returnStmt, int index) {
            this.returnStmt = returnStmt;
            this.returnIndex = index;
        }
        
        boolean hasSetterCalls() {
            return !setterCalls.isEmpty();
        }
        
        boolean hasReturnStmt() {
            return returnStmt != null;
        }
        
        List<MethodCallExpr> getSetterCalls() {
            return setterCalls;
        }
        
        List<Integer> getSetterIndices() {
            return setterIndices;
        }
        
        ReturnStmt getReturnStmt() {
            return returnStmt;
        }
        
        int getCreationIndex() {
            return creationIndex;
        }
        
        int getReturnIndex() {
            return returnIndex;
        }
    }
}