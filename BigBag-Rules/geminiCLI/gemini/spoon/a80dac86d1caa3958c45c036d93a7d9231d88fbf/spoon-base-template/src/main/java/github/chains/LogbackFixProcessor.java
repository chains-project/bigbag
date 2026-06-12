package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtCompilationUnit;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogbackFixProcessor extends AbstractProcessor<CtClass<?>> {

    @Override
    public boolean isToBeProcessed(CtClass<?> candidate) {
        return "uk.gov.pay.adminusers.queue.event.EventMessageHandlerTest".equals(candidate.getQualifiedName());
    }

    @Override
    public void process(CtClass<?> element) {
        // Remove fields
        CtField<?> mockLogAppender = element.getField("mockLogAppender");
        if (mockLogAppender != null) mockLogAppender.delete();

        CtField<?> loggingEventArgumentCaptor = element.getField("loggingEventArgumentCaptor");
        if (loggingEventArgumentCaptor != null) loggingEventArgumentCaptor.delete();

        // Process methods
        for (CtMethod<?> method : element.getMethods()) {
            if (method.getBody() == null) continue;
            List<CtStatement> statementsToRemove = method.getBody().getStatements().stream()
                    .filter(stmt -> {
                        String s = stmt.toString();
                        return s.contains("mockLogAppender") ||
                               s.contains("loggingEventArgumentCaptor") ||
                               s.contains("ch.qos.logback.classic.Logger") ||
                               s.contains("LoggerFactory.getLogger") ||
                               s.contains("ILoggingEvent") ||
                               (s.contains("logger.") && (s.contains("setLevel") || s.contains("addAppender"))) ||
                               s.contains("logStatement"); 
                    })
                    .collect(Collectors.toList());

            statementsToRemove.forEach(CtStatement::delete);
        }
        
        // Remove imports
        try {
             // Try to get CU from position
             CtCompilationUnit cu = element.getPosition().getCompilationUnit();
             
             if (cu != null) {
                List<CtImport> importsToRemove = new ArrayList<>();
                for (CtImport imp : cu.getImports()) {
                    String impStr = imp.toString();
                    if (impStr.contains("ch.qos.logback") || impStr.contains("var;")) {
                        importsToRemove.add(imp);
                    }
                }
                importsToRemove.forEach(imp -> cu.getImports().remove(imp));
             }
        } catch (Exception e) {
            System.err.println("Error processing imports: " + e.getMessage());
            e.printStackTrace();
        }
    }
}