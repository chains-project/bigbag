package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ThrowStmt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws Exception {
        Path path = Paths.get("/workspace/singer/singer-commons/src/main/java/com/pinterest/singer/loggingaudit/client/AuditEventKafkaSender.java");
        FileInputStream in = new FileInputStream(path.toFile());
        CompilationUnit cu = StaticJavaParser.parse(in);
        in.close();

        ClassOrInterfaceDeclaration classDecl = cu.getClassByName("AuditEventKafkaSender")
                .orElseThrow(() -> new RuntimeException("Class not found"));

        // 1. Find serializer field and remove initializer
        FieldDeclaration serializerField = classDecl.getFieldByName("serializer")
                .orElseThrow(() -> new RuntimeException("Field serializer not found"));
        
        if (serializerField.getVariable(0).getInitializer().isPresent()) {
            serializerField.getVariable(0).removeInitializer();
            System.out.println("Removed initializer from serializer field.");
        }

        // 2. Find constructor and add initialization
        ConstructorDeclaration constructor = classDecl.getConstructors().get(0); // Assuming one constructor
        BlockStmt body = constructor.getBody();

        // Create: try { this.serializer = new TSerializer(); } catch (TException e) { throw new RuntimeException(e); }
        BlockStmt tryBlock = new BlockStmt();
        ObjectCreationExpr newTSerializer = new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "TSerializer"), new com.github.javaparser.ast.NodeList<>());
        AssignExpr assign = new AssignExpr(new NameExpr("this.serializer"), newTSerializer, AssignExpr.Operator.ASSIGN);
        tryBlock.addStatement(new ExpressionStmt(assign));

        BlockStmt catchBlock = new BlockStmt();
        ObjectCreationExpr newRuntimeException = new ObjectCreationExpr(null, new ClassOrInterfaceType(null, "RuntimeException"), new com.github.javaparser.ast.NodeList<>(new NameExpr("e")));
        catchBlock.addStatement(new ThrowStmt(newRuntimeException));

        CatchClause catchClause = new CatchClause(new Parameter(new ClassOrInterfaceType(null, "TException"), "e"), catchBlock);
        
        TryStmt tryStmt = new TryStmt(tryBlock, new com.github.javaparser.ast.NodeList<>(catchClause), null);

        body.addStatement(tryStmt);
        System.out.println("Added serializer initialization to constructor.");

        // Write back
        FileOutputStream out = new FileOutputStream(path.toFile());
        out.write(cu.toString().getBytes());
        out.close();
    }
}