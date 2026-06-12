package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException {
        String filePath = "/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java";
        File file = new File(filePath);

        CompilationUnit cu = StaticJavaParser.parse(file);

        // 1. Remove imports
        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
                .filter(id -> id.getNameAsString().startsWith("ch.qos.logback"))
                .collect(Collectors.toList());
        importsToRemove.forEach(ImportDeclaration::remove);

        // 2. Remove fields
        List<FieldDeclaration> fieldsToRemove = cu.findAll(FieldDeclaration.class).stream()
                .filter(fd -> fd.getVariables().stream().anyMatch(v -> 
                        v.getNameAsString().equals("mockLogAppender") || 
                        v.getNameAsString().equals("loggingEventArgumentCaptor")))
                .collect(Collectors.toList());
        fieldsToRemove.forEach(FieldDeclaration::remove);

        // 3. Remove statements in methods
        cu.findAll(Statement.class).stream()
                .filter(stmt -> !stmt.isBlockStmt()) // IMPORTANT: Do not remove blocks
                .filter(stmt -> {
                    String s = stmt.toString();
                    return s.contains("mockLogAppender") || 
                           s.contains("loggingEventArgumentCaptor") ||
                           s.contains("ch.qos.logback") ||
                           s.contains("ILoggingEvent") ||
                           (s.contains("logger.") && (s.contains("addAppender") || s.contains("setLevel"))) ||
                           (s.contains("Logger logger = (Logger)")) ||
                           s.contains("logStatement"); // Added this
                })
                .forEach(Statement::remove);

        Files.write(Paths.get(filePath), cu.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("Transformation complete.");
    }
}