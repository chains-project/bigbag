package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        File file = new File("/workspace/flink-faker/src/main/java/com/github/knaufk/flink/faker/DateTime.java");
        CompilationUnit cu = StaticJavaParser.parse(file);

        ClassOrInterfaceDeclaration dateTimeClass = cu.getClassByName("DateTime").orElseThrow(() -> new RuntimeException("Class DateTime not found"));

        // Remove all methods. This effectively lets DateTime inherit all methods from DateAndTime,
        // which now return Timestamp (what we wanted), avoiding signature mismatches.
        dateTimeClass.getMethods().forEach(MethodDeclaration::remove);

        // Save
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(cu.toString());
        }
        
        System.out.println("Transformation complete.");
    }
}