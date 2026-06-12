package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) throws FileNotFoundException {
        File file = new File("/workspace/jadler/jadler-jetty/src/main/java/net/jadler/stubbing/server/jetty/JettyStubHttpServer.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Modify imports
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.eclipse.jetty.server.Connector"));
        cu.getImports().removeIf(i -> i.getNameAsString().equals("org.eclipse.jetty.server.nio.SelectChannelConnector"));
        cu.addImport("org.eclipse.jetty.server.ServerConnector");
        cu.addImport("org.eclipse.jetty.server.HttpConfiguration");
        cu.addImport("org.eclipse.jetty.server.HttpConnectionFactory");

        ClassOrInterfaceDeclaration clazz = cu.getClassByName("JettyStubHttpServer").get();

        // Change field type
        FieldDeclaration connectorField = clazz.getFieldByName("httpConnector").get();
        connectorField.getVariable(0).setType("ServerConnector");

        // Modify constructor
        ConstructorDeclaration constructor = clazz.getConstructors().stream()
                .filter(c -> c.getParameters().size() == 1 && c.getParameter(0).getNameAsString().equals("port"))
                .findFirst().get();

        BlockStmt body = constructor.getBody();
        body.getStatements().removeIf(stmt -> {
            if (stmt.isExpressionStmt()) {
                Expression expr = stmt.asExpressionStmt().getExpression();
                if (expr.isMethodCallExpr()) {
                    String methodName = expr.asMethodCallExpr().getNameAsString();
                    if (methodName.equals("setSendServerVersion") || methodName.equals("setSendDateHeader")) {
                        return true;
                    }
                } else if (expr.isAssignExpr()) {
                    Expression target = expr.asAssignExpr().getTarget();
                    if (target.isFieldAccessExpr() && target.asFieldAccessExpr().getNameAsString().equals("httpConnector")) {
                        if (expr.asAssignExpr().getValue().isObjectCreationExpr()) {
                            return true;
                        }
                    }
                }
            }
            return false;
        });

        // Insert new statements
        int insertIndex = -1;
        for (int i = 0; i < body.getStatements().size(); i++) {
            Statement stmt = body.getStatement(i);
            if (stmt.isExpressionStmt() && stmt.asExpressionStmt().getExpression().isAssignExpr()) {
                Expression target = stmt.asExpressionStmt().getExpression().asAssignExpr().getTarget();
                if (target.isFieldAccessExpr() && target.asFieldAccessExpr().getNameAsString().equals("server")) {
                    insertIndex = i + 1;
                    break;
                }
            }
        }

        if (insertIndex != -1) {
            body.addStatement(insertIndex, StaticJavaParser.parseStatement("HttpConfiguration httpConfig = new HttpConfiguration();"));
            body.addStatement(insertIndex + 1, StaticJavaParser.parseStatement("httpConfig.setSendServerVersion(false);"));
            body.addStatement(insertIndex + 2, StaticJavaParser.parseStatement("httpConfig.setSendDateHeader(true);"));
            body.addStatement(insertIndex + 3, StaticJavaParser.parseStatement("this.httpConnector = new ServerConnector(this.server, new HttpConnectionFactory(httpConfig));"));
        }

        PrintWriter out = new PrintWriter(file);
        out.println(cu.toString());
        out.close();
    }
}
