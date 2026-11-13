package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class FlywayApiMigrationProcessor extends AbstractProcessor<CtType<?>> {
    
    @Override
    public void process(CtType<?> ctType) {
        Factory factory = ctType.getFactory();
        
        // Find all Flyway constructor calls with no arguments (our target pattern)
        List<CtNewClass> flywayNewClasses = ctType.getElements(new TypeFilter<>(CtNewClass.class) {
            @Override
            public boolean matches(CtNewClass element) {
                return element.getType().getQualifiedName().equals("org.flywaydb.core.Flyway") &&
                       element.getArguments().isEmpty();
            }
        });
        
        // Process each Flyway new class creation
        for (CtNewClass newClass : flywayNewClasses) {
            // Replace the constructor with FluentConfiguration.configure()
            CtExecutableReference configureRef = factory.createExecutableReference();
            configureRef.setStatic(true);
            configureRef.setDeclaringType(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration"));
            configureRef.setSimpleName("configure");
            
            // Create FluentConfiguration.configure() invocation
            CtNewClass newFlywayCall = factory.createNewClass();
            newFlywayCall.setType(newClass.getType());
            newFlywayCall.setArguments(List.of(
                factory.createInvocation(
                    factory.createTypeAccess(factory.Type().createReference("org.flywaydb.core.api.configuration.FluentConfiguration")),
                    configureRef
                )
            ));
            
            // Replace the old constructor call
            newClass.replace(newFlywayCall);
        }
    }
}