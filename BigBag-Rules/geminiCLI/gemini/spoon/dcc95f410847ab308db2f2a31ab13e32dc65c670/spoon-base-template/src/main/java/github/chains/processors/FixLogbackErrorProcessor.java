package github.chains.processors;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FixLogbackErrorProcessor extends AbstractProcessor<CtType<?>> {

    @Override
    public boolean isToBeProcessed(CtType<?> candidate) {
        return candidate.getQualifiedName().equals("uk.gov.pay.adminusers.queue.event.EventMessageHandlerTest");
    }

    @Override
    public void process(CtType<?> element) {
        System.out.println("Processing " + element.getQualifiedName());
        Set<String> variablesToRemove = new HashSet<>();
        variablesToRemove.add("mockLogAppender");
        variablesToRemove.add("loggingEventArgumentCaptor");
        variablesToRemove.add("logger");
        variablesToRemove.add("logStatement");

        // 1. Collect statements to remove
        List<CtStatement> statementsToRemove = element.getElements(new TypeFilter<CtStatement>(CtStatement.class)).stream()
                .filter(stmt -> shouldRemove(stmt, variablesToRemove))
                .collect(Collectors.toList());

        System.out.println("Found " + statementsToRemove.size() + " statements to remove.");

        // 2. Remove statements
        for (CtStatement stmt : statementsToRemove) {
            System.out.println("Deleting: " + stmt.toString());
            if (stmt.getParent() != null) {
                try {
                    stmt.delete();
                } catch (Exception e) {
                    System.out.println("Failed to delete: " + e.getMessage());
                }
            } else {
                System.out.println("Parent is null for: " + stmt);
            }
        }

        // 3. Remove fields
        element.getFields().stream()
                .filter(f -> variablesToRemove.contains(f.getSimpleName()))
                .collect(Collectors.toList()) // Avoid concurrent mod
                .forEach(CtElement::delete);
        
        // 4. Remove imports
        if (element.getPosition() != null && element.getPosition().getCompilationUnit() != null) {
             element.getPosition().getCompilationUnit().getImports().stream()
                .filter(imp -> {
                    CtReference ref = imp.getReference();
                    return ref != null && ref instanceof CtTypeReference && ((CtTypeReference<?>) ref).getQualifiedName().startsWith("ch.qos.logback");
                })
                .collect(Collectors.toList())
                .forEach(CtImport::delete);
        }
    }

    private boolean shouldRemove(CtStatement stmt, Set<String> variables) {
        if (stmt instanceof spoon.reflect.code.CtBlock) return false;
        if (stmt instanceof spoon.reflect.code.CtIf) return false;
        if (stmt instanceof spoon.reflect.code.CtLoop) return false;
        if (stmt instanceof spoon.reflect.code.CtTry) return false;
        if (stmt instanceof spoon.reflect.code.CtSwitch) return false;
        if (stmt instanceof spoon.reflect.code.CtSynchronized) return false;

        String code = stmt.toString();
        for (String var : variables) {
            if (code.contains(var)) {
                return true;
            }
        }
        
        return false;
    }
}