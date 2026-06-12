package github.chains;

import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.util.List;

/**
 * Generic visitor that fixes SnakeYAML constructor calls 
 * from SnakeYAML 1.x to 2.0
 */
public class SnakeYamlFixVisitor extends CtScanner {
    private final Factory factory;
    
    public SnakeYamlFixVisitor(Factory factory) {
        this.factory = factory;
    }
    
    @Override
    public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
        // Check if this is a Yaml constructor call with a Constructor parameter
        if (isYamlConstructorWithConstructor(ctConstructorCall)) {
            // Add the LoaderOptions parameter to fix the SnakeYAML 2.0 API change
            addLoaderOptionsParameter(ctConstructorCall);
        }
        super.visitCtConstructorCall(ctConstructorCall);
    }
    
    private boolean isYamlConstructorWithConstructor(CtConstructorCall<?> ctConstructorCall) {
        // Check if this is a Yaml constructor call
        if (ctConstructorCall.getType() == null) return false;
        if (!ctConstructorCall.getType().getQualifiedName().equals("org.yaml.snakeyaml.Yaml")) {
            return false;
        }
        
        // Check if it has exactly one argument that is a Constructor
        List<?> arguments = ctConstructorCall.getArguments();
        if (arguments.size() != 1) {
            return false;
        }
        
        Object arg = arguments.get(0);
        if (!(arg instanceof CtConstructorCall)) {
            return false;
        }
        
        CtConstructorCall<?> constructorArg = (CtConstructorCall<?>) arg;
        if (constructorArg.getType() == null) {
            return false;
        }
        
        // Check if it's a Constructor type
        return constructorArg.getType().getQualifiedName().equals("org.yaml.snakeyaml.constructor.Constructor");
    }
    
    private void addLoaderOptionsParameter(CtConstructorCall<?> ctConstructorCall) {
        // Create new LoaderOptions() parameter
        CtConstructorCall<?> loaderOptionsCall = factory.Core().createConstructorCall();
        loaderOptionsCall.setType(factory.Type().createReference("org.yaml.snakeyaml.LoaderOptions"));
        loaderOptionsCall.setArguments(List.of());
        
        // Add the LoaderOptions parameter to the existing constructor call
        ctConstructorCall.getArguments().add(loaderOptionsCall);
    }
}