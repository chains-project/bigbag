package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.code.CtNewClass;

import java.util.List;

public class DependencyGraphBuilderFixProcessor extends AbstractProcessor {
    
    @Override
    public void process(CtType<?> element) {
        // Find all imports that reference the deprecated class
        List<CtImport> imports = element.getElements(new TypeFilter<>(CtImport.class));
        
        for (CtImport imp : imports) {
            if (imp.getReference() != null) {
                String qualifiedName = imp.getReference().getQualifiedName();
                if (qualifiedName.equals("org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder")) {
                    // Remove the old import
                    imp.delete();
                    
                    // Add new import for the replacement
                    CtImport newImport = getFactory().createImport(
                        getFactory().createTypeReference("org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder")
                    );
                    element.addImport(newImport);
                }
            }
        }
        
        // Find and replace new class instantiations 
        List<CtNewClass> newClasses = element.getElements(new TypeFilter<>(CtNewClass.class));
        for (CtNewClass newClass : newClasses) {
            if (newClass.getType() != null && 
                newClass.getType().getQualifiedName().equals("org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder")) {
                // Replace the type with the new one  
                CtTypeReference<?> newRef = getFactory().createTypeReference("org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder");
                newClass.setType(newRef);
            }
        }
    }
}