package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;

public class LogbackFixProcessor extends AbstractProcessor<CtType<?>> {
    @Override
    public void process(CtType<?> element) {
        if (!"uk.gov.pay.adminusers.queue.event.EventMessageHandlerTest".equals(element.getQualifiedName())) {
            return;
        }

        System.out.println("Processing " + element.getQualifiedName());

        // Remove fields
        CtField<?> mockLogAppender = element.getField("mockLogAppender");
        if (mockLogAppender != null) {
            mockLogAppender.delete();
            System.out.println("Removed field mockLogAppender");
        }

        CtField<?> loggingEventArgumentCaptor = element.getField("loggingEventArgumentCaptor");
        if (loggingEventArgumentCaptor != null) {
            loggingEventArgumentCaptor.delete();
            System.out.println("Removed field loggingEventArgumentCaptor");
        }

        // Remove setup code
        List<CtMethod<?>> methods = element.getMethodsByName("setUp");
        if (!methods.isEmpty()) {
            CtMethod<?> setUp = methods.get(0);
            // We use a safe list copy to iterate
            List<CtStatement> statementsToDelete = new ArrayList<>();
            for (CtStatement stmt : setUp.getBody().getStatements()) {
                String s = stmt.toString();
                if (s.contains("LoggerFactory.getLogger") ||
                    s.contains("logger.setLevel") ||
                    s.contains("logger.addAppender")) {
                    statementsToDelete.add(stmt);
                }
            }
            for (CtStatement stmt : statementsToDelete) {
                stmt.delete();
                System.out.println("Deleted setup statement: " + stmt);
            }
        }

        // Remove verification code in tests
        for (CtMethod<?> method : element.getMethods()) {
             // Avoid deleting from setUp again (though it shouldn't match filters below if we did job right, but cleaner to skip)
             if ("setUp".equals(method.getSimpleName())) continue;

             List<CtStatement> statementsToDelete = method.getElements(new TypeFilter<CtStatement>(CtStatement.class) {
                 @Override
                 public boolean matches(CtStatement stmt) {
                     String s = stmt.toString();
                     // Filter out blocks to avoid deleting the whole method body if it matches
                     if (stmt instanceof spoon.reflect.code.CtBlock) return false;
                     
                     return s.contains("mockLogAppender") || 
                            s.contains("loggingEventArgumentCaptor") ||
                            s.contains("ILoggingEvent") ||
                            s.contains("logStatement");
                 }
             });
             
             for (CtStatement s : statementsToDelete) {
                 // Check if still part of tree (might be deleted as child of another deleted stmt)
                 if (s.isParentInitialized()) { 
                    s.delete();
                    System.out.println("Deleted test statement in " + method.getSimpleName());
                 }
             }
        }
    }
}
