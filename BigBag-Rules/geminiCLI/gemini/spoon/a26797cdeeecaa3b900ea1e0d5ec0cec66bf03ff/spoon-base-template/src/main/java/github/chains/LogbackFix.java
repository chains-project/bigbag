package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.util.List;
import java.util.stream.Collectors;

public class LogbackFix {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false); // Disable auto imports to avoid "import var;"
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Output to the same directory structure. 
        launcher.setSourceOutputDirectory("/workspace/pay-adminusers/src/test/java");

        CtModel model = launcher.buildModel();
        
        // Remove bad imports
        model.getAllTypes().forEach(type -> {
            type.getFactory().CompilationUnit().getMap().values().forEach(cu -> {
                List<spoon.reflect.declaration.CtImport> importsToRemove = cu.getImports().stream()
                    .filter(i -> {
                        String s = i.toString();
                        return s.contains("import var;") || s.contains("ch.qos.logback");
                    })
                    .collect(Collectors.toList());
                
                for (spoon.reflect.declaration.CtImport i : importsToRemove) {
                    System.out.println("Removing import: " + i);
                    cu.getImports().remove(i);
                }
            });
        });

        for (CtType<?> type : model.getAllTypes()) {
            if (type instanceof CtClass && type.getSimpleName().equals("EventMessageHandlerTest")) {
                CtClass<?> clazz = (CtClass<?>) type;
                
                System.out.println("Processing " + clazz.getQualifiedName());

                // Remove fields
                removeField(clazz, "mockLogAppender");
                removeField(clazz, "loggingEventArgumentCaptor");

                // Process methods
                for (CtMethod<?> method : clazz.getMethods()) {
                    processMethod(method);
                }
            }
        }
        
        launcher.prettyprint();
    }

    private static void removeField(CtClass<?> clazz, String fieldName) {
        CtField<?> field = clazz.getField(fieldName);
        if (field != null) {
            System.out.println("Removing field: " + fieldName);
            clazz.removeField(field);
        }
    }

    private static void processMethod(CtMethod<?> method) {
        if (method.getBody() == null) return;

        List<CtStatement> statementsToRemove = method.getBody().getStatements().stream()
            .filter(stmt -> shouldRemove(stmt))
            .collect(Collectors.toList());
            
        for (CtStatement stmt : statementsToRemove) {
            System.out.println("Removing statement in " + method.getSimpleName() + ": " + stmt.toString().split("\n")[0] + "...");
            method.getBody().removeStatement(stmt);
        }
    }

    private static boolean shouldRemove(CtStatement stmt) {
        String s = stmt.toString();
        if (s.contains("mockLogAppender")) return true;
        if (s.contains("loggingEventArgumentCaptor")) return true;
        if (s.contains("ch.qos.logback")) return true;
        if (s.contains("logStatement")) return true;
        
        // Specific checks for the logger setup
        if (s.contains("LoggerFactory.getLogger") && s.contains("(Logger)")) return true;
        if (s.contains("logger.setLevel")) return true;
        if (s.contains("logger.addAppender")) return true;
        
        return false;
    }
}
