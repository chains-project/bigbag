package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;

public class FixSonarLintEngineProcessor extends AbstractProcessor<CtMethod<?>> {

    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        return candidate.getSimpleName().equals("buildAnalysisEngineConfiguration")
                && candidate.getDeclaringType().getSimpleName().equals("SonarLintEngine");
    }

    @Override
    public void process(CtMethod<?> method) {
        System.out.println("Processing method: " + method.getSignature());
        
        if (method.getBody().getStatements().isEmpty()) {
            return;
        }
        CtStatement stmt = method.getBody().getStatement(0);
        if (!(stmt instanceof CtReturn)) {
            return;
        }
        CtReturn<?> returnStmt = (CtReturn<?>) stmt;
        if (!(returnStmt.getReturnedExpression() instanceof CtInvocation)) {
            return;
        }
        CtInvocation<?> invocation = (CtInvocation<?>) returnStmt.getReturnedExpression();
        
        CtInvocation<?> targetInvocation = null;
        CtInvocation<?> parentOfTarget = null;
        CtInvocation<?> current = invocation;
        
        while (current != null) {
            if (current.getExecutable().getSimpleName().equals("addEnabledLanguages")) {
                targetInvocation = current;
                break;
            }
            if (current.getTarget() instanceof CtInvocation) {
                parentOfTarget = current;
                current = (CtInvocation<?>) current.getTarget();
            } else {
                current = null;
            }
        }
        
        if (targetInvocation == null) {
            System.out.println("Could not find addEnabledLanguages invocation");
            return;
        }
        System.out.println("Found addEnabledLanguages invocation");

        // Remove the invocation from the chain.
        CtExpression<?> previousTarget = targetInvocation.getTarget();
        
        if (parentOfTarget != null) {
            parentOfTarget.setTarget(previousTarget);
            System.out.println("Removed addEnabledLanguages invocation from chain.");
        } else {
            System.out.println("Warning: addEnabledLanguages seems to be the last call? This processor expects it in a chain.");
        }
    }
}
