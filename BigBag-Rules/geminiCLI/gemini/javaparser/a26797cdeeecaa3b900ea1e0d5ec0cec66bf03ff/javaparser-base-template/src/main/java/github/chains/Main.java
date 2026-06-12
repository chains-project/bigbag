package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Remove imports
        cu.getImports().removeIf(id -> id.getNameAsString().startsWith("ch.qos.logback"));

        // Remove fields
        List<FieldDeclaration> fieldsToRemove = new ArrayList<>();
        cu.findAll(FieldDeclaration.class).forEach(fd -> {
            fd.getVariables().removeIf(v -> v.getNameAsString().equals("mockLogAppender"));
            fd.getVariables().removeIf(v -> v.getNameAsString().equals("loggingEventArgumentCaptor"));
            if (fd.getVariables().isEmpty()) {
                fieldsToRemove.add(fd);
            }
        });
        fieldsToRemove.forEach(FieldDeclaration::remove);

        // Remove statements
        // Collect statements to remove first to avoid concurrent modification exceptions if iterating and modifying
        List<Statement> stmtsToRemove = new ArrayList<>();
        cu.findAll(Statement.class).forEach(stmt -> {
            if (stmt instanceof BlockStmt) return; // Don't remove blocks themselves

            String stmtStr = stmt.toString();
            if (stmtStr.contains("mockLogAppender") || 
                stmtStr.contains("loggingEventArgumentCaptor") || 
                stmtStr.contains("logStatement") ||
                (stmtStr.contains("Logger logger") && stmtStr.contains("LoggerFactory.getLogger")) ||
                (stmtStr.contains("logger.setLevel")) ||
                (stmtStr.contains("logger.addAppender"))) {
                stmtsToRemove.add(stmt);
            }
        });
        
        stmtsToRemove.forEach(Statement::remove);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
        System.out.println("Transformation complete.");
    }
}
