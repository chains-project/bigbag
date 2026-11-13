package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Main {
    
    // Configuration for the transformation - parameterizable for different projects
    private static final String[] TARGET_CLASSES = {
        "org.apache.thrift.TSerializer",
        "org.apache.thrift.TDeserializer"
    };
    
    private static final String EXCEPTION_TYPE = "org.apache.thrift.transport.TTransportException";
    
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        List<Path> javaFiles = findAllJavaFiles(sourceDir);
        System.out.println("Found " + javaFiles.size() + " Java files to process");
        
        int modifiedFiles = 0;
        for (Path javaFile : javaFiles) {
            if (processFile(javaFile)) {
                modifiedFiles++;
            }
        }
        
        System.out.println("Successfully modified " + modifiedFiles + " files");
    }
    
    private static List<Path> findAllJavaFiles(String sourceDir) throws IOException {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(Paths.get(sourceDir))
            .filter(Files::isRegularFile)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(Path filePath) throws IOException {
        JavaParser parser = new JavaParser();
        Optional<CompilationUnit> cuOpt = parser.parse(filePath).getResult();
        
        if (!cuOpt.isPresent()) {
            System.err.println("Failed to parse: " + filePath);
            return false;
        }
        
        CompilationUnit cu = cuOpt.get();
        ConstructorExceptionVisitor visitor = new ConstructorExceptionVisitor();
        
        boolean modified = visitor.visit(cu, null) != null;
        
        if (modified) {
            // Write the modified file back
            PrinterConfiguration config = new DefaultPrinterConfiguration();
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(config);
            String newContent = printer.print(cu);
            
            Files.write(filePath, newContent.getBytes());
            System.out.println("Modified: " + filePath);
            return true;
        }
        
        return false;
    }
    
    static class ConstructorExceptionVisitor extends ModifierVisitor<Void> {
        
        @Override
        public Visitable visit(ObjectCreationExpr n, Void arg) {
            // Check if this is a constructor call for one of our target classes
            String typeName = getFullyQualifiedTypeName(n);
            
            if (isTargetClass(typeName)) {
                // Check the context where this constructor is called
                Node parent = n.getParentNode().orElse(null);
                
                if (parent instanceof VariableDeclarator) {
                    // This is a field or local variable declaration with initialization
                    VariableDeclarator declarator = (VariableDeclarator) parent;
                    Node grandParent = parent.getParentNode().orElse(null);
                    
                    if (grandParent instanceof FieldDeclaration) {
                        // Field declaration with initialization - need to move to constructor
                        handleFieldInitialization((FieldDeclaration) grandParent, declarator, n);
                        return n;
                    }
                } else if (parent instanceof AssignExpr) {
                    // Assignment expression - check if it's a field assignment
                    AssignExpr assignExpr = (AssignExpr) parent;
                    if (isFieldAssignment(assignExpr)) {
                        // Field assignment - need to handle in constructor or method
                        handleFieldAssignment(assignExpr, n);
                        return n;
                    }
                }
                
                // For other contexts (method local variables, return statements, etc.)
                // just ensure the enclosing method declares the exception
                ensureEnclosingMethodDeclaresException(n);
            }
            
            return super.visit(n, arg);
        }
        
        private String getFullyQualifiedTypeName(ObjectCreationExpr expr) {
            if (expr.getType().isClassOrInterfaceType()) {
                ClassOrInterfaceType type = expr.getType().asClassOrInterfaceType();
                return type.getNameAsString();
            }
            return "";
        }
        
        private boolean isTargetClass(String typeName) {
            for (String targetClass : TARGET_CLASSES) {
                String simpleName = targetClass.substring(targetClass.lastIndexOf('.') + 1);
                if (simpleName.equals(typeName)) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean isFieldAssignment(AssignExpr assignExpr) {
            Expression target = assignExpr.getTarget();
            return target instanceof FieldAccessExpr || target instanceof NameExpr;
        }
        
        private void handleFieldInitialization(FieldDeclaration fieldDecl, VariableDeclarator declarator, ObjectCreationExpr creationExpr) {
            // Get the field name
            String fieldName = declarator.getNameAsString();
            
            // Find or create a constructor to initialize this field
            ClassOrInterfaceDeclaration classDecl = findEnclosingClass(fieldDecl);
            if (classDecl != null) {
                // Initialize field in all constructors
                boolean initializedInAnyConstructor = false;
                for (BodyDeclaration<?> member : classDecl.getMembers()) {
                    if (member instanceof ConstructorDeclaration) {
                        ConstructorDeclaration constructor = (ConstructorDeclaration) member;
                        addFieldInitializationToConstructor(constructor, fieldName, creationExpr);
                        initializedInAnyConstructor = true;
                    }
                }
                
                // If no constructors exist, create a default one
                if (!initializedInAnyConstructor) {
                    ConstructorDeclaration newConstructor = createNoArgConstructor(classDecl);
                    addFieldInitializationToConstructor(newConstructor, fieldName, creationExpr);
                    classDecl.addMember(newConstructor);
                }
                
                // Remove the initializer from the field
                declarator.removeInitializer();
            }
        }
        
        private void handleFieldAssignment(AssignExpr assignExpr, ObjectCreationExpr creationExpr) {
            // For field assignments, we need to ensure they're in a method that declares the exception
            ensureEnclosingMethodDeclaresException(assignExpr);
        }
        
        private void ensureEnclosingMethodDeclaresException(Node node) {
            MethodDeclaration enclosingMethod = findEnclosingMethod(node);
            if (enclosingMethod != null && !methodDeclaresException(enclosingMethod, EXCEPTION_TYPE)) {
                addExceptionToMethod(enclosingMethod, EXCEPTION_TYPE);
            }
        }
        
        private ClassOrInterfaceDeclaration findEnclosingClass(Node node) {
            Node current = node;
            while (current != null) {
                if (current instanceof ClassOrInterfaceDeclaration) {
                    return (ClassOrInterfaceDeclaration) current;
                }
                current = current.getParentNode().orElse(null);
            }
            return null;
        }
        
        private MethodDeclaration findEnclosingMethod(Node node) {
            Node current = node;
            while (current != null) {
                if (current instanceof MethodDeclaration) {
                    return (MethodDeclaration) current;
                }
                current = current.getParentNode().orElse(null);
            }
            return null;
        }
        
        private Optional<ConstructorDeclaration> findNoArgConstructor(ClassOrInterfaceDeclaration classDecl) {
            for (BodyDeclaration<?> member : classDecl.getMembers()) {
                if (member instanceof ConstructorDeclaration) {
                    ConstructorDeclaration constructor = (ConstructorDeclaration) member;
                    if (constructor.getParameters().isEmpty()) {
                        return Optional.of(constructor);
                    }
                }
            }
            return Optional.empty();
        }
        
        private ConstructorDeclaration createNoArgConstructor(ClassOrInterfaceDeclaration classDecl) {
            ConstructorDeclaration constructor = new ConstructorDeclaration();
            constructor.setName(classDecl.getNameAsString());
            constructor.setPublic(true);
            constructor.setBody(new BlockStmt());
            return constructor;
        }
        
        private void addFieldInitializationToConstructor(ConstructorDeclaration constructor, String fieldName, ObjectCreationExpr creationExpr) {
            // Check if field is already initialized in this constructor
            boolean alreadyInitialized = false;
            for (Statement stmt : constructor.getBody().getStatements()) {
                if (stmt instanceof ExpressionStmt) {
                    ExpressionStmt exprStmt = (ExpressionStmt) stmt;
                    if (exprStmt.getExpression() instanceof AssignExpr) {
                        AssignExpr assignExpr = (AssignExpr) exprStmt.getExpression();
                        if (assignExpr.getTarget() instanceof FieldAccessExpr) {
                            FieldAccessExpr fieldAccess = (FieldAccessExpr) assignExpr.getTarget();
                            if (fieldAccess.getNameAsString().equals(fieldName)) {
                                alreadyInitialized = true;
                                break;
                            }
                        }
                    }
                }
            }
            
            if (!alreadyInitialized) {
                // Create assignment statement: this.fieldName = new TSerializer();
                FieldAccessExpr fieldAccess = new FieldAccessExpr(new ThisExpr(), fieldName);
                AssignExpr assignment = new AssignExpr(fieldAccess, creationExpr.clone(), AssignExpr.Operator.ASSIGN);
                ExpressionStmt stmt = new ExpressionStmt(assignment);
                
                // Add to constructor body at the beginning
                constructor.getBody().addStatement(0, stmt);
                
                // Ensure constructor declares the exception
                if (!constructorDeclaresException(constructor, EXCEPTION_TYPE)) {
                    addExceptionToConstructor(constructor, EXCEPTION_TYPE);
                }
            }
        }
        
        private boolean methodDeclaresException(MethodDeclaration method, String exceptionType) {
            for (com.github.javaparser.ast.type.ReferenceType exception : method.getThrownExceptions()) {
                if (exception.toString().equals(exceptionType) || 
                    exception.toString().equals("TTransportException")) {
                    return true;
                }
            }
            return false;
        }
        
        private boolean constructorDeclaresException(ConstructorDeclaration constructor, String exceptionType) {
            for (com.github.javaparser.ast.type.ReferenceType exception : constructor.getThrownExceptions()) {
                if (exception.toString().equals(exceptionType) || 
                    exception.toString().equals("TTransportException")) {
                    return true;
                }
            }
            return false;
        }
        
        private void addExceptionToMethod(MethodDeclaration method, String exceptionType) {
            ClassOrInterfaceType exception = new ClassOrInterfaceType(null, exceptionType);
            method.addThrownException(exception);
        }
        
        private void addExceptionToConstructor(ConstructorDeclaration constructor, String exceptionType) {
            ClassOrInterfaceType exception = new ClassOrInterfaceType(null, exceptionType);
            constructor.addThrownException(exception);
        }
    }
}