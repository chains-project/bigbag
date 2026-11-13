package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    
    // Classes whose constructors now throw TTransportException in libthrift 0.16.0
    private static final Set<String> AFFECTED_CLASSES = Set.of(
        "org.apache.thrift.TSerializer",
        "org.apache.thrift.TDeserializer"
    );
    
    private static final String TTRANSPORT_EXCEPTION = "org.apache.thrift.transport.TTransportException";
    
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        Path sourceDir = Paths.get(args[0]);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            System.err.println("Error: Source directory does not exist: " + sourceDir);
            System.exit(1);
        }
        
        System.out.println("Processing Java files in: " + sourceDir);
        
        List<Path> javaFiles;
        try {
            javaFiles = Files.walk(sourceDir)
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error walking directory: " + e.getMessage());
            return;
        }
            
        System.out.println("Found " + javaFiles.size() + " Java files");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Modified " + modifiedFiles + " files");
    }
    
    private static boolean processFile(Path javaFile) {
        JavaParser parser = new JavaParser();
        CompilationUnit cu;
        try {
            cu = parser.parse(javaFile).getResult().orElse(null);
        } catch (Exception e) {
            System.err.println("Error parsing file " + javaFile + ": " + e.getMessage());
            return false;
        }
        if (cu == null) {
            return false;
        }
        
        boolean modified = false;
        
        // First pass: collect field declarations that need fixing
        List<FieldDeclaration> fieldsToFix = new ArrayList<>();
        cu.accept(new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(FieldDeclaration n, Void arg) {
                for (VariableDeclarator vd : n.getVariables()) {
                    if (vd.getInitializer().isPresent() && 
                        isAffectedConstructorCall(vd.getInitializer().get())) {
                        fieldsToFix.add(n);
                        break;
                    }
                }
                return super.visit(n, arg);
            }
        }, null);
        
        // Fix field declarations by moving initialization to constructors
        for (FieldDeclaration field : fieldsToFix) {
            fixFieldInitialization(cu, field);
            modified = true;
        }
        
        // Second pass: fix local variable declarations
        ThriftConstructorFixVisitor visitor = new ThriftConstructorFixVisitor();
        CompilationUnit modifiedCu = (CompilationUnit) cu.accept(visitor, null);
        if (visitor.isModified()) {
            modified = true;
            cu = modifiedCu;
        }
        
        if (modified) {
            // Write back the modified file
            try {
                Files.write(javaFile, cu.toString().getBytes());
                System.out.println("Fixed: " + javaFile);
                return true;
            } catch (Exception e) {
                System.err.println("Error writing file " + javaFile + ": " + e.getMessage());
            }
        }
        
        return false;
    }
    
    private static void fixFieldInitialization(CompilationUnit cu, FieldDeclaration field) {
        // Get the enclosing class
        Optional<ClassOrInterfaceDeclaration> enclosingClass = field.findAncestor(ClassOrInterfaceDeclaration.class);
        if (!enclosingClass.isPresent()) {
            return;
        }
        
        ClassOrInterfaceDeclaration classDecl = enclosingClass.get();
        
        // For each variable in the field
        for (VariableDeclarator vd : field.getVariables()) {
            if (vd.getInitializer().isPresent() && isAffectedConstructorCall(vd.getInitializer().get())) {
                // Save the initializer before removing it
                Expression initializer = vd.getInitializer().get().clone();
                
                // Remove initializer from field
                vd.setInitializer((Expression) null);
                
                // Add initialization to all constructors
                for (ConstructorDeclaration constructor : classDecl.getConstructors()) {
                    addFieldInitializationToConstructor(constructor, vd, initializer);
                }
                
                // If no constructors exist, create a default one
                if (classDecl.getConstructors().isEmpty()) {
                    ConstructorDeclaration defaultConstructor = classDecl.addConstructor();
                    defaultConstructor.setBody(new BlockStmt());
                    addFieldInitializationToConstructor(defaultConstructor, vd, initializer);
                }
            }
        }
    }
    
    private static void addFieldInitializationToConstructor(ConstructorDeclaration constructor, VariableDeclarator vd, Expression initializer) {
        BlockStmt body = constructor.getBody();
        String fieldName = vd.getNameAsString();
        
        // Create a simple initialization with try-catch
        // this.field = new TSerializer();
        FieldAccessExpr fieldAccess = new FieldAccessExpr(new ThisExpr(), fieldName);
        AssignExpr assignment = new AssignExpr(fieldAccess, initializer.clone(), AssignExpr.Operator.ASSIGN);
        
        // Wrap in try-catch
        TryStmt tryStmt = new TryStmt();
        BlockStmt tryBlock = new BlockStmt();
        tryBlock.addStatement(new ExpressionStmt(assignment));
        tryStmt.setTryBlock(tryBlock);
        
        // Catch TTransportException and wrap in RuntimeException
        CatchClause catchClause = new CatchClause();
        catchClause.setParameter(new com.github.javaparser.ast.body.Parameter(
            new ClassOrInterfaceType(null, TTRANSPORT_EXCEPTION),
            "e"
        ));
        BlockStmt catchBlock = new BlockStmt();
        catchBlock.addStatement(new ThrowStmt(
            new ObjectCreationExpr(
                null,
                new ClassOrInterfaceType(null, "RuntimeException"),
                NodeList.nodeList(new NameExpr("e"))
            )
        ));
        catchClause.setBody(catchBlock);
        tryStmt.setCatchClauses(NodeList.nodeList(catchClause));
        
        // Add at the beginning of constructor
        body.getStatements().addFirst(tryStmt);
    }
    
    private static boolean isAffectedConstructorCall(Expression expr) {
        if (!(expr instanceof ObjectCreationExpr)) {
            return false;
        }
        
        ObjectCreationExpr objectCreation = (ObjectCreationExpr) expr;
        String typeName = objectCreation.getType().asString();
        
        // Check if it's one of the affected classes
        return AFFECTED_CLASSES.stream()
            .anyMatch(affected -> affected.endsWith("." + typeName) || affected.equals(typeName));
    }
    
    private static class ThriftConstructorFixVisitor extends ModifierVisitor<Void> {
        private boolean modified = false;
        
        public boolean isModified() {
            return modified;
        }
        
        @Override
        public Visitable visit(VariableDeclarator n, Void arg) {
            // Check if this is a local variable with an affected constructor call
            if (n.getInitializer().isPresent() && isAffectedConstructorCall(n.getInitializer().get())) {
                // Check if it's a field (already handled) or local variable
                Optional<FieldDeclaration> fieldDecl = n.findAncestor(FieldDeclaration.class);
                if (!fieldDecl.isPresent()) {
                    // This is a local variable
                    Optional<MethodDeclaration> methodDecl = n.findAncestor(MethodDeclaration.class);
                    Optional<ConstructorDeclaration> constructorDecl = n.findAncestor(ConstructorDeclaration.class);
                    
                    if (methodDecl.isPresent() || constructorDecl.isPresent()) {
                        // Check if already declares TTransportException
                        boolean declaresException = false;
                        if (methodDecl.isPresent()) {
                            declaresException = methodDecl.get().getThrownExceptions().stream()
                                .anyMatch(ex -> ex.asString().equals(TTRANSPORT_EXCEPTION) || 
                                               ex.asString().equals("TTransportException"));
                        } else if (constructorDecl.isPresent()) {
                            declaresException = constructorDecl.get().getThrownExceptions().stream()
                                .anyMatch(ex -> ex.asString().equals(TTRANSPORT_EXCEPTION) || 
                                               ex.asString().equals("TTransportException"));
                        }
                        
                        if (!declaresException) {
                            // Need to wrap in try-catch
                            wrapLocalVariableInTryCatch(n);
                        }
                    }
                }
            }
            
            return super.visit(n, arg);
        }
        
        private void wrapLocalVariableInTryCatch(VariableDeclarator vd) {
            modified = true;
            
            // Get the parent statement (should be an ExpressionStmt containing VariableDeclarationExpr)
            Optional<Node> parent = vd.getParentNode();
            if (!parent.isPresent()) {
                return;
            }
            
            // We need to replace the entire statement with a try-catch block
            // Create a new block that will replace the original variable declaration
            String varName = vd.getNameAsString();
            String typeName = vd.getType().asString();
            Expression constructorCall = vd.getInitializer().get();
            
            // Create temporary variable name
            String tempVarName = varName + "Temp";
            
            // Create try block
            BlockStmt tryBlock = new BlockStmt();
            
            // Declare and initialize temp variable in try block
            VariableDeclarationExpr tempVarDecl = new VariableDeclarationExpr(
                new com.github.javaparser.ast.type.ClassOrInterfaceType(null, typeName),
                tempVarName
            );
            tempVarDecl.getVariable(0).setInitializer(constructorCall.clone());
            tryBlock.addStatement(new ExpressionStmt(tempVarDecl));
            
            // Assign temp to actual variable
            AssignExpr assignToActual = new AssignExpr(
                new NameExpr(varName),
                new NameExpr(tempVarName),
                AssignExpr.Operator.ASSIGN
            );
            tryBlock.addStatement(new ExpressionStmt(assignToActual));
            
            // Create catch block
            CatchClause catchClause = new CatchClause();
            catchClause.setParameter(new com.github.javaparser.ast.body.Parameter(
                new ClassOrInterfaceType(null, TTRANSPORT_EXCEPTION),
                "e"
            ));
            BlockStmt catchBlock = new BlockStmt();
            catchBlock.addStatement(new ThrowStmt(
                new ObjectCreationExpr(
                    null,
                    new ClassOrInterfaceType(null, "RuntimeException"),
                    NodeList.nodeList(new NameExpr("e"))
                )
            ));
            catchClause.setBody(catchBlock);
            
            // Create try statement
            TryStmt tryStmt = new TryStmt();
            tryStmt.setTryBlock(tryBlock);
            tryStmt.setCatchClauses(NodeList.nodeList(catchClause));
            
            // Now we need to replace the parent statement with the try-catch block
            // The parent should be a VariableDeclarationExpr, whose parent is an ExpressionStmt
            if (parent.get() instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr) {
                com.github.javaparser.ast.expr.VariableDeclarationExpr varDeclExpr = 
                    (com.github.javaparser.ast.expr.VariableDeclarationExpr) parent.get();
                Optional<Node> grandParent = varDeclExpr.getParentNode();
                if (grandParent.isPresent() && grandParent.get() instanceof ExpressionStmt) {
                    // Replace the ExpressionStmt with the try-catch block
                    grandParent.get().replace(tryStmt);
                }
            }
        }
    }
}