package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import java.util.List;

/**
 * Generic Spoon transformation to fix Hibernate UserType nullSafeSet method signature
 * breaking change from SessionImplementor to SharedSessionContractImplementor
 */
public class Main {
    public static void main(String[] args) {
        // Set the input directory (this can be parameterized)
        String inputPath = "/workspace/onebusaway-gtfs-modules";
        if (args.length > 0) {
            inputPath = args[0];
        }
        
        // Process the source files
        Launcher launcher = new Launcher();
        launcher.addInputResource(inputPath);
        launcher.setSourceOutputDirectory("/tmp/transformed");
        launcher.addProcessor(new HibernateUserTypeFixProcessor());
        launcher.run();
    }
}

class HibernateUserTypeFixProcessor extends AbstractProcessor<CtMethod<?>> {
    
    @Override
    public void process(CtMethod<?> method) {
        // Look for methods named nullSafeSet in UserType implementations
        if ("nullSafeSet".equals(method.getSimpleName())) {
            // Check if the method belongs to a class implementing UserType
            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType != null) {
                // Check if the class implements UserType
                for (CtTypeReference<?> iface : declaringType.getSuperInterfaces()) {
                    if ("org.hibernate.usertype.UserType".equals(iface.getQualifiedName())) {
                        // Check if the method has the wrong signature (SessionImplementor instead of SharedSessionContractImplementor)
                        List<CtParameter<?>> parameters = method.getParameters();
                        if (parameters.size() >= 4) {
                            CtParameter<?> lastParam = parameters.get(3);
                            if ("org.hibernate.engine.spi.SessionImplementor".equals(lastParam.getType().getQualifiedName())) {
                                // Fix the method signature by replacing the parameter type
                                fixNullSafeSetMethod(method, lastParam);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void fixNullSafeSetMethod(CtMethod<?> method, CtParameter<?> oldParameter) {
        // Get the factory
        Factory factory = method.getFactory();
        
        // Create the new parameter with correct type
        CtTypeReference<?> newTypeRef = factory.Type().createReference("org.hibernate.engine.spi.SharedSessionContractImplementor");
        
        // Create a new parameter with the correct type
        CtParameter<?> newParameter = factory.Core().createParameter();
        newParameter.setType(newTypeRef);
        newParameter.setSimpleName(oldParameter.getSimpleName());
        newParameter.setAnnotations(oldParameter.getAnnotations());
        
        // Replace the old parameter with the new one
        List<CtParameter<?>> parameters = method.getParameters();
        int index = parameters.indexOf(oldParameter);
        if (index >= 0) {
            parameters.set(index, newParameter);
            method.setParameters(parameters);
        }
    }
}