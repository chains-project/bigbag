package github.chains;

import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class FlywayFixVisitor extends CtScanner {
    private Factory factory;

    public FlywayFixVisitor(Factory factory) {
        this.factory = factory;
    }

    @Override
    public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
        super.visitCtConstructorCall(ctConstructorCall);
        
        // Check if this is a Flyway constructor with no arguments
        if (ctConstructorCall.getType() != null && 
            ctConstructorCall.getType().getSimpleName().equals("Flyway") &&
            ctConstructorCall.getArguments().isEmpty()) {
            
            // Create new constructor call with FluentConfiguration.configure().load()
            CtExpression<?> fluentConfig = factory.createInvocation(
                factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration")),
                factory.createExecutableReference().setSimpleName("configure")
            );
            
            // Add .load() call
            CtExpression<?> loadCall = factory.createInvocation(
                fluentConfig,
                factory.createExecutableReference().setSimpleName("load")
            );
            
            // Replace the constructor call with new one
            ctConstructorCall.replace(factory.createConstructorCall(
                ctConstructorCall.getType(),
                loadCall
            ));
        }
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> ctInvocation) {
        super.visitCtInvocation(ctInvocation);
        
        // Check if this is a Flyway method call that needs to be replaced
        if (ctInvocation.getTarget() instanceof CtExpression) {
            CtExpression<?> target = (CtExpression<?>) ctInvocation.getTarget();
            if (target.getType() != null && target.getType().getSimpleName().equals("Flyway")) {
                String methodName = ctInvocation.getExecutable().getSimpleName();
                
                // Map old method names to new fluent API method names
                String newMethodName = "";
                switch (methodName) {
                    case "setDataSource":
                        newMethodName = "dataSource";
                        break;
                    case "setClassLoader":
                        newMethodName = "classLoader";
                        break;
                    case "setLocations":
                        newMethodName = "locations";
                        break;
                    case "setValidateOnMigrate":
                        newMethodName = "validateOnMigrate";
                        break;
                    default:
                        // Skip unknown methods
                        return;
                }
                
                // Create a new invocation that uses the Fluent API
                CtExpression<?>[] args = ctInvocation.getArguments().toArray(new CtExpression[0]);
                
                // Create the new fluent call
                CtInvocation<?> newCall = factory.createInvocation(
                    ctInvocation.getTarget(),
                    factory.createExecutableReference().setSimpleName(newMethodName),
                    args
                );
                
                // Replace with new fluent call
                ctInvocation.replace(newCall);
            }
        }
    }
}