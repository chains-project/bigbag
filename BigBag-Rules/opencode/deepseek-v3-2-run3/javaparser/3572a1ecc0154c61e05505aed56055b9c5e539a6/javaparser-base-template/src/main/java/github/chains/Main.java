package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.stmt.ThrowStmt;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
    
    // Configuration: Classes that moved packages
    private static final Set<String> MOVED_CLASSES = new HashSet<>(Arrays.asList(
        "TFastFramedTransport",
        "TFramedTransport"
    ));
    
    // Old package prefix
    private static final String OLD_PACKAGE = "org.apache.thrift.transport";
    
    // New package prefix for layered transports
    private static final String NEW_LAYERED_PACKAGE = "org.apache.thrift.transport.layered";
    
    // Classes whose constructors throw TTransportException
    private static final Set<String> EXCEPTION_THROWING_CONSTRUCTORS = new HashSet<>(Arrays.asList(
        "TFastFramedTransport",
        "TFramedTransport", 
        "TIOStreamTransport",
        "TDeserializer",
        "TSerializer"
    ));
    
    // Exception type
    private static final String TRANSPORT_EXCEPTION = "TTransportException";
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory>");
            System.err.println("\nThis transformation handles libthrift 0.16.0 breaking changes:");
            System.err.println("1. Transport classes moved to 'layered' package");
            System.err.println("2. Constructors now throw TTransportException");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        System.out.println("Handling breaking changes for: " + String.join(", ", EXCEPTION_THROWING_CONSTRUCTORS));
        
        try {
            List<Path> javaFiles = findJavaFiles(Paths.get(sourceDir));
            System.out.println("Found " + javaFiles.size() + " Java files");
            
            int updatedFiles = 0;
            JavaParser parser = new JavaParser();
            
            for (Path javaFile : javaFiles) {
                boolean fileUpdated = processFile(parser, javaFile);
                if (fileUpdated) {
                    updatedFiles++;
                }
            }
            
            System.out.println("Updated " + updatedFiles + " files");
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private static List<Path> findJavaFiles(Path directory) throws Exception {
        List<Path> javaFiles = new ArrayList<>();
        Files.walk(directory)
            .filter(path -> path.toString().endsWith(".java"))
            .forEach(javaFiles::add);
        return javaFiles;
    }
    
    private static boolean processFile(JavaParser parser, Path javaFile) throws Exception {
        System.out.println("Processing: " + javaFile);
        
        CompilationUnit cu;
        try (FileInputStream in = new FileInputStream(javaFile.toFile())) {
            cu = parser.parse(in).getResult().orElse(null);
        }
        
        if (cu == null) {
            return false;
        }
        
        boolean fileUpdated = false;
        
        // Update imports for moved classes
        fileUpdated = updateImports(cu) || fileUpdated;
        
        // Update constructor calls that now throw exceptions
        ConstructorVisitor visitor = new ConstructorVisitor();
        visitor.visit(cu, null);
        fileUpdated = visitor.isUpdated() || fileUpdated;
        
        if (fileUpdated) {
            try (FileWriter writer = new FileWriter(javaFile.toFile())) {
                writer.write(cu.toString());
            }
            System.out.println("  File updated successfully");
        }
        
        return fileUpdated;
    }
    
    private static boolean updateImports(CompilationUnit cu) {
        boolean updated = false;
        NodeList<ImportDeclaration> imports = cu.getImports();
        
        for (int i = 0; i < imports.size(); i++) {
            ImportDeclaration imp = imports.get(i);
            String importName = imp.getNameAsString();
            
            // Check if this import is for a moved class
            for (String className : MOVED_CLASSES) {
                String oldFullName = OLD_PACKAGE + "." + className;
                if (importName.equals(oldFullName)) {
                    System.out.println("  Found moved class import: " + importName);
                    
                    ImportDeclaration newImport = new ImportDeclaration(
                        new Name(NEW_LAYERED_PACKAGE + "." + className),
                        imp.isStatic(),
                        imp.isAsterisk()
                    );
                    
                    imports.set(i, newImport);
                    updated = true;
                    System.out.println("  Updated to: " + NEW_LAYERED_PACKAGE + "." + className);
                }
            }
        }
        
        return updated;
    }
    
    private static class ConstructorVisitor extends VoidVisitorAdapter<Void> {
        private boolean updated = false;
        
        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            
            String typeName = expr.getType().asString();
            
            // Check if this is a constructor that now throws TTransportException
            if (EXCEPTION_THROWING_CONSTRUCTORS.contains(typeName)) {
                System.out.println("  Found " + typeName + " constructor call");
                
                // Check if this constructor call is already properly handled
                if (isAlreadyHandled(expr)) {
                    System.out.println("    Already properly handled, skipping");
                    return;
                }
                
                // Try to wrap in try-catch
                if (wrapInTryCatch(expr)) {
                    updated = true;
                }
            }
        }
        
        private boolean isAlreadyHandled(ObjectCreationExpr expr) {
            // Check if already in a try block that catches TTransportException
            return expr.findAncestor(TryStmt.class)
                .map(tryStmt -> {
                    for (CatchClause catchClause : tryStmt.getCatchClauses()) {
                        Parameter param = catchClause.getParameter();
                        String caughtType = param.getType().asString();
                        if (caughtType.equals(TRANSPORT_EXCEPTION) || 
                            caughtType.equals(OLD_PACKAGE + "." + TRANSPORT_EXCEPTION) ||
                            caughtType.equals(NEW_LAYERED_PACKAGE + "." + TRANSPORT_EXCEPTION)) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
        }
        
        private boolean wrapInTryCatch(ObjectCreationExpr expr) {
            // Find the parent statement containing this expression
            if (!expr.getParentNode().isPresent()) {
                return false;
            }
            
            Object parent = expr.getParentNode().get();
            
            // Handle assignment expressions
            if (parent instanceof AssignExpr) {
                AssignExpr assignExpr = (AssignExpr) parent;
                if (assignExpr.getParentNode().isPresent() && 
                    assignExpr.getParentNode().get() instanceof ExpressionStmt) {
                    
                    ExpressionStmt stmt = (ExpressionStmt) assignExpr.getParentNode().get();
                    return wrapStatementInTryCatch(stmt);
                }
            }
            
            // Handle expression statements directly
            if (parent instanceof ExpressionStmt) {
                ExpressionStmt stmt = (ExpressionStmt) parent;
                return wrapStatementInTryCatch(stmt);
            }
            
            // Handle variable declaration expressions
            if (parent instanceof com.github.javaparser.ast.expr.VariableDeclarationExpr) {
                com.github.javaparser.ast.expr.VariableDeclarationExpr varDecl = 
                    (com.github.javaparser.ast.expr.VariableDeclarationExpr) parent;
                if (varDecl.getParentNode().isPresent() && 
                    varDecl.getParentNode().get() instanceof ExpressionStmt) {
                    
                    ExpressionStmt stmt = (ExpressionStmt) varDecl.getParentNode().get();
                    return wrapStatementInTryCatch(stmt);
                }
            }
            
            return false;
        }
        
        private boolean wrapStatementInTryCatch(ExpressionStmt stmt) {
            try {
                // Create try statement
                TryStmt tryStmt = new TryStmt();
                
                // Create try block with the statement
                NodeList<com.github.javaparser.ast.stmt.Statement> tryBlockStatements = new NodeList<>();
                tryBlockStatements.add(stmt.clone());
                BlockStmt tryBlock = new BlockStmt(tryBlockStatements);
                
                // Create catch clause for TTransportException
                NodeList<CatchClause> catchClauses = new NodeList<>();
                
                // Catch parameter
                Parameter param = new Parameter(
                    new ClassOrInterfaceType(null, TRANSPORT_EXCEPTION),
                    "e"
                );
                
                // Catch block - wrap in RuntimeException
                NodeList<com.github.javaparser.ast.stmt.Statement> catchBlockStatements = new NodeList<>();
                catchBlockStatements.add(
                    new ThrowStmt(
                        new ObjectCreationExpr(
                            null,
                            new ClassOrInterfaceType(null, "RuntimeException"),
                            NodeList.nodeList(new NameExpr("e"))
                        )
                    )
                );
                
                BlockStmt catchBlock = new BlockStmt(catchBlockStatements);
                CatchClause catchClause = new CatchClause(param, catchBlock);
                catchClauses.add(catchClause);
                
                tryStmt.setTryBlock(tryBlock);
                tryStmt.setCatchClauses(catchClauses);
                
                // Replace the statement with try statement
                stmt.replace(tryStmt);
                System.out.println("    Wrapped statement in try-catch block");
                return true;
                
            } catch (Exception e) {
                System.err.println("    Error wrapping statement: " + e.getMessage());
                return false;
            }
        }
        
        public boolean isUpdated() {
            return updated;
        }
    }
}