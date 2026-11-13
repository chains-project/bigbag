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
import java.util.Set;
import java.util.HashSet;

public class LogbackDependencyFixer implements Template {
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
        List<CtClass<?>> implementingClasses = type.getFactory().Class().get(implementingClass);
        for (CtClass<?> clazz : implementingClasses) {
            // Remove the interface from the implements clause
            clazz.removeSuperInterface(clazz.getFactory().Type().createReference(removedInterface));
        }
    }
}