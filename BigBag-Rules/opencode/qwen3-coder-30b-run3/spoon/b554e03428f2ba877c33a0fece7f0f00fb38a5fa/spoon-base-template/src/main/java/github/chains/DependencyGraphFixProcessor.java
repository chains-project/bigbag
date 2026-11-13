package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

/**
 * Spoon processor to fix Maven dependency tree API breaking change
 * Replaces Maven31DependencyGraphBuilder with DefaultDependencyGraphBuilder
 */
public class DependencyGraphFixProcessor extends AbstractProcessor {
    
    @Override
    public void process(CtType<?> element) {
        // Find all constructor calls that instantiate Maven31DependencyGraphBuilder
        List<CtConstructorCall<?>> constructorCalls = element.getElements(CtConstructorCall.class);
        
        for (CtConstructorCall<?> constructorCall : constructorCalls) {
            // Check if the constructor call is for Maven31DependencyGraphBuilder
            if (constructorCall.getType() != null && 
                constructorCall.getType().getQualifiedName().contains("Maven31DependencyGraphBuilder")) {
                
                // Replace with DefaultDependencyGraphBuilder
                String fullyQualifiedClassName = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
                
                // Change the constructor call to use DefaultDependencyGraphBuilder
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(fullyQualifiedClassName);
                constructorCall.setType(newTypeRef);
            }
        }
    }
}