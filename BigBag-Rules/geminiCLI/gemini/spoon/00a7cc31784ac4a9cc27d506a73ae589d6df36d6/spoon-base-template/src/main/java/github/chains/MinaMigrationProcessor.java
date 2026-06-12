package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtStatement;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.reference.CtExecutableReference;

public class MinaMigrationProcessor extends AbstractProcessor<CtClass<?>> {
    @Override
    public void process(CtClass<?> ctClass) {
        // Fix SSLFilter constructor
        if ("SSLFilter".equals(ctClass.getSimpleName())) {
            for (CtConstructor<?> c : ctClass.getConstructors()) {
                if (c.getParameters().size() == 2) {
                    // SSLFilter(SSLContext, boolean)
                    // Modify super call
                     CtBlock<?> body = c.getBody();
                     if (body != null) {
                         for (CtStatement stmt : body.getStatements()) {
                             if (stmt instanceof CtInvocation) {
                                 CtInvocation<?> inv = (CtInvocation<?>) stmt;
                                 if (inv.getExecutable().getSimpleName().equals("<init>")) {
                                     // Remove second argument
                                     if (inv.getArguments().size() == 2) {
                                         inv.removeArgument(inv.getArguments().get(1));
                                     }
                                 }
                             }
                         }
                     }
                }
            }
            
            // Fix PEER_ADDRESS usage in setAttribute
            ctClass.getElements(new TypeFilter<CtInvocation<?>>(CtInvocation.class)).forEach(inv -> {
                 if ("setAttribute".equals(inv.getExecutable().getSimpleName())) {
                      if (!inv.getArguments().isEmpty()) {
                           CtExpression<?> arg0 = inv.getArguments().get(0);
                           if (arg0.toString().contains("PEER_ADDRESS")) {
                                // Remove the invocation
                                inv.delete(); 
                           }
                      }
                 }
            });
        }
        
        // Remove setUseClientMode calls and rename initiateHandshake
        for (CtMethod<?> method : ctClass.getMethods()) {
            for (CtInvocation<?> inv : method.getElements(new TypeFilter<>(CtInvocation.class))) {
                CtExecutableReference<?> exec = inv.getExecutable();
                if ("setUseClientMode".equals(exec.getSimpleName())) {
                    // Remove the invocation
                    inv.delete();
                }
                
                if ("initiateHandshake".equals(exec.getSimpleName())) {
                     // Rename to startSsl
                     exec.setSimpleName("startSsl");
                }
            }
        }
    }
}
