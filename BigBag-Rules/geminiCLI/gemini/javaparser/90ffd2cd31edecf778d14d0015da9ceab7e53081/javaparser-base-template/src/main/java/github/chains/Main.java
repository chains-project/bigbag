package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.stmt.Statement;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        // Remove Logback imports
        List<ImportDeclaration> importsToRemove = cu.getImports().stream()
            .filter(importDecl -> importDecl.getNameAsString().startsWith("ch.qos.logback"))
            .collect(Collectors.toList());
        importsToRemove.forEach(ImportDeclaration::remove);

        ClassOrInterfaceDeclaration testClass = cu.getClassByName("EventMessageHandlerTest").orElseThrow();

        // Remove fields 'mockLogAppender' and 'loggingEventArgumentCaptor'
        List<FieldDeclaration> fieldsToRemove = testClass.getFields().stream()
            .filter(field -> field.getVariables().stream().anyMatch(v -> 
                v.getNameAsString().equals("mockLogAppender") || v.getNameAsString().equals("loggingEventArgumentCaptor")
            ))
            .collect(Collectors.toList());
        fieldsToRemove.forEach(FieldDeclaration::remove);

        // Remove code from setUp method that uses logger/mockLogAppender
        testClass.getMethodsByName("setUp").forEach(method -> {
            method.getBody().ifPresent(body -> {
                List<Statement> statementsToRemove = body.getStatements().stream()
                    .filter(stmt -> {
                        String s = stmt.toString();
                        return s.contains("Logger logger =") || 
                               s.contains("logger.setLevel") || 
                               s.contains("logger.addAppender");
                    })
                    .collect(Collectors.toList());
                statementsToRemove.forEach(Statement::remove);
            });
        });

        // Remove code from tests that uses mockLogAppender or loggingEventArgumentCaptor
        List<MethodDeclaration> testMethods = testClass.getMethods().stream()
            .filter(m -> m.getAnnotationByName("Test").isPresent())
            .collect(Collectors.toList());
        
        testMethods.forEach(method -> {
             method.getBody().ifPresent(body -> {
                List<Statement> statementsToRemove = body.getStatements().stream()
                    .filter(stmt -> {
                        String s = stmt.toString();
                        return s.contains("mockLogAppender") || 
                               s.contains("loggingEventArgumentCaptor") ||
                               s.contains("logStatement"); // List<ILoggingEvent> logStatement ...
                    })
                    .collect(Collectors.toList());
                 statementsToRemove.forEach(Statement::remove);
             });
        });

        Files.write(file.toPath(), cu.toString().getBytes());
        System.out.println("Transformation applied successfully.");
    }
}
