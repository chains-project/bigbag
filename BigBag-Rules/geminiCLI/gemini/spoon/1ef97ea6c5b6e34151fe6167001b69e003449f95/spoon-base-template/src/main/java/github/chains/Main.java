package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.DefaultJavaPrettyPrinter;
import java.util.List;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] args) {
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource("/workspace/flink-faker/src/main/java");
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(true);
            
            launcher.buildModel();
            
            for (CtType<?> type : launcher.getFactory().Class().getAll()) {
                if (type.getQualifiedName().equals("com.github.knaufk.flink.faker.DateTime")) {
                    System.out.println("Processing DateTime class...");
                    
                    // Remove birthday methods
                    List<CtMethod<?>> methodsToRemove = new ArrayList<>();
                    for (CtMethod<?> method : type.getMethods()) {
                        if (method.getSimpleName().equals("birthday")) {
                            methodsToRemove.add(method);
                        }
                    }
                    for (CtMethod<?> method : methodsToRemove) {
                        type.removeMethod(method);
                    }
                    
                    // Fix between method
                    List<CtMethod<?>> betweenMethods = type.getMethodsByName("between");
                    for (CtMethod<?> method : betweenMethods) {
                        List<CtAnnotation<?>> annotations = method.getAnnotations();
                        CtAnnotation<?> overrideAnnotation = null;
                        for (CtAnnotation<?> ann : annotations) {
                            if (ann.getType().getSimpleName().equals("Override")) {
                                overrideAnnotation = ann;
                                break;
                            }
                        }
                        if (overrideAnnotation != null) {
                            method.removeAnnotation(overrideAnnotation);
                        }
                        method.setBody(launcher.getFactory().Code().createCodeSnippetStatement(
                            "return new java.sql.Timestamp(super.between(new java.sql.Timestamp(from.getTime()), new java.sql.Timestamp(to.getTime())).getTime())"
                        ));
                    }
                    
                    // Get Compilation Unit
                    CtCompilationUnit cu = type.getPosition().getCompilationUnit();
                    
                    // Pretty print
                    DefaultJavaPrettyPrinter printer = new DefaultJavaPrettyPrinter(launcher.getEnvironment());
                    printer.calculate(cu, new ArrayList<>());
                    String content = printer.getResult();
                    
                    String outputPath = "/workspace/flink-faker/src/main/java/com/github/knaufk/flink/faker/DateTime.java";
                    System.out.println("Saving modified DateTime.java to " + outputPath);
                    Files.write(Paths.get(outputPath), content.getBytes(StandardCharsets.UTF_8));
                }
            }
            System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}