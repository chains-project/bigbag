package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;

public class FixDependencyGraphBuilderProcessor extends AbstractProcessor<CtMethod<?>> {

    @Override
    public boolean isToBeProcessed(CtMethod<?> candidate) {
        return candidate.getSimpleName().equals("setGraph")
                && candidate.getDeclaringType().getQualifiedName().equals("com.mycila.maven.plugin.license.dependencies.MavenProjectLicenses");
    }

    @Override
    public void process(CtMethod<?> method) {
        // Replace the body with "this.graph = graph;"
        method.getBody().getStatements().clear();
        method.getBody().addStatement(getFactory().createCodeSnippetStatement("this.graph = graph"));
        
        // Remove the import
        method.getPosition().getCompilationUnit().getImports().removeIf(imp -> 
            imp.toString().contains("Maven31DependencyGraphBuilder")
        );
    }
}
