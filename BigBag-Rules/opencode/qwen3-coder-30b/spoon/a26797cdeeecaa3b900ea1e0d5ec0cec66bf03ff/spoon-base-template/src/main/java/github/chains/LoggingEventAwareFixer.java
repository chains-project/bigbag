package github.chains;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.template.Template;
import spoon.template.TemplateParameter;

import java.util.List;

public class LoggingEventAwareFixer implements Template {
    // The fully qualified name of the interface that was removed
    private final String removedInterface = "org.slf4j.spi.LoggingEventAware";
    
    // The fully qualified name of the class that was implementing it
    private final String implementingClass = "ch.qos.logback.classic.Logger";
    
    public void process(CtType<?> type) {
        Factory factory = type.getFactory();
        
        // Remove imports of the removed interface
        List<CtImport> imports = type.getFactory().CompilationUnit().getDeclaredImports();
        for (CtImport imp : imports) {
            if (imp.getReference() != null && 
                imp.getReference().getQualifiedName().equals(removedInterface)) {
                imp.delete();
            }
        }
        
        // Find all classes that implement the removed interface
        List<CtClass<?>> implementingClasses = factory.Class().getAll();
        for (CtClass<?> clazz : implementingClasses) {
            // Remove the interface from the implements clause
            CtTypeReference<?> interfaceRef = factory.Type().createReference(removedInterface);
            if (clazz.getSuperInterfaces().contains(interfaceRef)) {
                clazz.removeSuperInterface(interfaceRef);
            }
        }
        
        // Remove any references to the interface in type casts or method signatures
        // This handles cases where the interface is used in method parameters or return types
        type.filterChildren(new TypeFilter<CtTypeReference<?>>(CtTypeReference.class) {
            @Override
            public boolean matches(CtTypeReference<?> element) {
                if (removedInterface.equals(element.getQualifiedName())) {
                    // Remove the reference
                    element.delete();
                    return true;
                }
                return false;
            }
        });
    }
}