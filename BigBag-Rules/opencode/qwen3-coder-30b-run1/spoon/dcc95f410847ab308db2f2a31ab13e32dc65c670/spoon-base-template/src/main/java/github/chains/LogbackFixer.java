package github.chains;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class LogbackFixer {
    
    public static void fixLogbackBreakingChanges(CtType<?> targetType) {
        Factory factory = targetType.getFactory();
        
        // Find all invocations of setIncludeCallerData() without parameters in logback classes
        List<CtInvocation> invocations = targetType.getElements(new TypeFilter<CtInvocation>(CtInvocation.class) {
            @Override
            public boolean matches(CtInvocation element) {
                if (element.getExecutable() == null) return false;
                
                String methodName = element.getExecutable().getSimpleName();
                if (!"setIncludeCallerData".equals(methodName)) return false;
                
                // Check if it's a logback class that had this method signature changed
                if (element.getExecutable().getDeclaringType() == null) return false;
                
                String declaringTypeName = element.getExecutable().getDeclaringType().getQualifiedName();
                return declaringTypeName != null && 
                       (declaringTypeName.equals("ch.qos.logback.classic.AsyncAppender") ||
                        declaringTypeName.equals("ch.qos.logback.classic.net.SMTPAppender") ||
                        declaringTypeName.equals("ch.qos.logback.classic.net.SSLSocketAppender") ||
                        declaringTypeName.equals("ch.qos.logback.classic.net.SocketAppender") ||
                        declaringTypeName.equals("ch.qos.logback.classic.net.server.SSLServerSocketAppender") ||
                        declaringTypeName.equals("ch.qos.logback.classic.net.server.ServerSocketAppender"));
            }
        });
        
        // Replace each invocation with the new method call that includes the boolean parameter
        for (CtInvocation invocation : invocations) {
            // Create new invocation with the same target but with true parameter (most common use case)
            CtInvocation newInvocation = factory.Code().createInvocation(
                invocation.getTarget(), 
                invocation.getExecutable(), 
                factory.Code().createLiteral(true)
            );
            
            // Replace the old invocation with the new one
            invocation.replace(newInvocation);
        }
    }
}