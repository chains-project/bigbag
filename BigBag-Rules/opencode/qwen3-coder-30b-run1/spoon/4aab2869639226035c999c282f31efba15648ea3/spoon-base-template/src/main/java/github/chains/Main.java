package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public class Main extends AbstractProcessor<CtInvocation<?>> {
    
    @Override
    public void process(CtInvocation<?> invocation) {
        // Check if this is a call to Storages.newStorage()
        if (invocation.getExecutable() != null && 
            "newStorage".equals(invocation.getExecutable().getSimpleName()) &&
            invocation.getTarget() != null) {
            
            // Get the target type
            if (invocation.getTarget().getType() != null && 
                "com.artipie.asto.factory.Storages".equals(invocation.getTarget().getType().getQualifiedName())) {
                
                // This is the old pattern: new Storages().newStorage(type, config)
                // We need to transform it to: new StoragesLoader().newObject(type, config)
                
                // Get the factory to create new references
                Factory factory = getFactory();
                
                // Replace the target type from Storages to StoragesLoader
                invocation.getTarget().setType(factory.Type().createReference("com.artipie.asto.factory.StoragesLoader"));
                
                // Replace the method name from newStorage to newObject
                invocation.setExecutable(factory.Executable().createReference("newObject"));
                
                System.out.println("Transformed: " + invocation.toString());
            }
        }
    }
    
    public static void main(String[] args) {
        System.out.println("Spoon transformation ready to fix Storages.newStorage -> StoragesLoader.newObject");
    }
}