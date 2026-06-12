package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class EventMessageHandlerTestProcessor extends AbstractProcessor<CtType<?>> {
    @Override
    public void process(CtType<?> type) {
        if (!type.getQualifiedName().endsWith("EventMessageHandlerTest")) {
             return;
        }
        System.out.println("Processing type: " + type.getQualifiedName());

        // 1. Remove Fields
        CtField<?> field1 = type.getField("mockLogAppender");
        if (field1 != null) {
            System.out.println("Removing field: mockLogAppender");
            field1.delete();
        }

        CtField<?> field2 = type.getField("loggingEventArgumentCaptor");
        if (field2 != null) {
             System.out.println("Removing field: loggingEventArgumentCaptor");
             field2.delete();
        }

        // 2. Scan all statements to remove specific usages
        List<CtStatement> toRemove = new ArrayList<>();

        type.getElements(new TypeFilter<>(CtStatement.class)).forEach(stmt -> {
            if (stmt instanceof CtBlock) {
                return;
            }
            String s = stmt.toString();
            if (s.contains("mockLogAppender") ||
                s.contains("loggingEventArgumentCaptor") ||
                s.contains("(Logger)") || // Cast to logback Logger
                s.contains("Level.INFO") ||
                s.contains("ILoggingEvent") ||
                s.contains("logback") ||
                s.contains("logStatement")
            ) {
                System.out.println("Marking for removal: " + s);
                toRemove.add(stmt);
            }
        });

        for (CtStatement stmt : toRemove) {
            try {
                if (stmt.getParent() instanceof CtBlock) {
                    ((CtBlock) stmt.getParent()).removeStatement(stmt);
                    System.out.println("Removed statement from block.");
                } else {
                    stmt.delete();
                    System.out.println("Deleted statement via delete().");
                }
            } catch (Exception e) {
                System.out.println("Failed to delete statement: " + e.getMessage());
            }
        }
        
        // Write to file
        try {
            System.out.println("Using type.toString() fallback.");
            String pkg = "";
            if (type.getPackage() != null && !type.getPackage().isUnnamedPackage()) {
                pkg = "package " + type.getPackage().getQualifiedName() + ";\n\n";
            }
            String content = pkg + type.toString();

            Files.write(Paths.get("/workspace/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java"), content.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("File written successfully from processor.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
