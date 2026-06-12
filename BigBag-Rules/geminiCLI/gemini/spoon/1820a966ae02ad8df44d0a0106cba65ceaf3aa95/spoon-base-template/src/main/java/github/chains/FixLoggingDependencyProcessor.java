package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FixLoggingDependencyProcessor extends AbstractProcessor<CtType<?>> {
    @Override
    public void process(CtType<?> element) {
        if (!element.getSimpleName().equals("EventMessageHandlerTest")) {
            return;
        }
        System.out.println("Processing type: " + element.getQualifiedName());

        Set<CtStatement> statementsToDelete = new HashSet<>();
        
        // Helper to find the statement in the block
        java.util.function.Function<CtElement, CtStatement> findBlockStatement = (el) -> {
            CtElement current = el;
            while (current != null) {
                if (current instanceof CtStatement && current.getParent() instanceof CtBlock) {
                    return (CtStatement) current;
                }
                current = current.getParent();
            }
            return null;
        };

        // 1. Identify fields to remove
        List<CtField<?>> fields = element.getElements(new TypeFilter<>(CtField.class));
        for (CtField<?> field : fields) {
            if (field.getSimpleName().equals("mockLogAppender") || field.getSimpleName().equals("loggingEventArgumentCaptor")) {
                System.out.println("Marking field for removal: " + field.getSimpleName());
                // Find usages of this field
                List<CtVariableAccess> usages = element.getElements(new TypeFilter<>(CtVariableAccess.class));
                for (CtVariableAccess usage : usages) {
                    if (usage.getVariable() != null && usage.getVariable().getSimpleName().equals(field.getSimpleName())) {
                        CtStatement stmt = findBlockStatement.apply(usage);
                        if (stmt != null) {
                            statementsToDelete.add(stmt);
                        }
                    }
                }
                field.delete();
            }
        }
        
        // 2. Identify local variables to remove
        List<CtLocalVariable<?>> locals = element.getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable<?> local : locals) {
            String name = local.getSimpleName();
            boolean remove = false;
            if (name.equals("logger") && local.getType().toString().contains("Logger")) {
                remove = true;
            }
            if (name.equals("logStatement")) {
                remove = true;
            }
            
            if (remove) {
                System.out.println("Marking local variable for removal: " + name);
                CtStatement declStmt = findBlockStatement.apply(local);
                if (declStmt != null) statementsToDelete.add(declStmt);
                
                List<CtVariableAccess> usages = element.getElements(new TypeFilter<>(CtVariableAccess.class));
                for (CtVariableAccess usage : usages) {
                    if (usage.getVariable() != null && usage.getVariable().getSimpleName().equals(name)) {
                        CtStatement stmt = findBlockStatement.apply(usage);
                        if (stmt != null) {
                            statementsToDelete.add(stmt);
                        }
                    }
                }
            }
        }

        System.out.println("Found " + statementsToDelete.size() + " statements to delete.");

        for (CtStatement stmt : statementsToDelete) {
             if (stmt.isParentInitialized()) {
                 try {
                     System.out.println("Deleting stmt: " + stmt.toString().substring(0, Math.min(stmt.toString().length(), 40)) + "...");
                     stmt.delete();
                 } catch (Exception e) {
                     // ignore
                 }
             }
        }
    }
}