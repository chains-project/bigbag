package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java -jar spoon-transformation.jar <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        launcher.addProcessor(new JAXBStrategyGetterProcessor());
        
        launcher.run();
    }
    
    static class JAXBStrategyGetterProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtExecutableReference<?> execRef = invocation.getExecutable();
            if (execRef == null) return;
            
            String methodName = execRef.getSimpleName();
            if (!"getInstance".equals(methodName)) return;
            
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            if (declaringType == null) return;
            
            String typeName = declaringType.getQualifiedName();
            if (!typeName.startsWith("org.jvnet.jaxb2_commons.lang.")) return;
            
            if (!typeName.endsWith("Strategy") && !typeName.endsWith("Strategy2")) {
                return;
            }
            
            // Create field access: TypeName.INSTANCE
            // Use code snippet expression
            Factory factory = getFactory();
            String fieldAccessCode = declaringType.getSimpleName() + ".INSTANCE";
            CtExpression<?> fieldAccess = factory.Code().createCodeSnippetExpression(fieldAccessCode);
            invocation.replace(fieldAccess);
            System.out.println("Transformed: " + typeName + ".getInstance() -> " + typeName + ".INSTANCE");
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            CtExecutableReference<?> execRef = candidate.getExecutable();
            if (execRef == null) return false;
            
            if (!"getInstance".equals(execRef.getSimpleName())) return false;
            
            CtTypeReference<?> declaringType = execRef.getDeclaringType();
            if (declaringType == null) return false;
            
            String typeName = declaringType.getQualifiedName();
            return typeName.startsWith("org.jvnet.jaxb2_commons.lang.") &&
                   (typeName.endsWith("Strategy") || typeName.endsWith("Strategy2"));
        }
    }
}