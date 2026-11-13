package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/java-pubsub-group-kafka-connector");
        launcher.setSourceOutputDirectory("/tmp/spoon-output");
        
        // Set the Java version to 8 to match the project
        launcher.getEnvironment().setComplianceLevel(8);
        
        // Process the code
        launcher.addProcessor(new AdminClientCreateSubscriptionFixProcessor());
        launcher.run();
        
        System.out.println("Transformation completed.");
    }
    
    /**
     * Processor to fix broken createSubscription calls that were changed from 1 to 2 arguments
     */
    public static class AdminClientCreateSubscriptionFixProcessor extends AbstractProcessor<CtInvocation> {
        @Override
        public boolean isToBeProcessed(CtInvocation candidate) {
            // Check if this is a call to createSubscription with 1 argument
            if (!"createSubscription".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }
            
            // Check the target type (it should be AdminClient)
            if (candidate.getTarget() == null) {
                return false;
            }
            
            // Check if the target is an AdminClient
            if (candidate.getTarget().getType() != null) {
                String targetTypeName = candidate.getTarget().getType().getQualifiedName();
                
                // Look for the AdminClient in the pubsublite packages
                if (targetTypeName.contains("com.google.cloud.pubsublite.AdminClient")) {
                    // Check that this call has exactly one argument (the subscription)
                    return candidate.getArguments().size() == 1;
                }
            }
            
            return false;
        }
        
        @Override
        public void process(CtInvocation element) {
            // Get the factory
            var factory = getFactory();
            
            // Create the BacklogLocation.END expression
            CtFieldAccess backlogLocationEnd = factory.createFieldRead();
            CtTypeReference backlogLocationRef = factory.Type().createReference("com.google.cloud.pubsublite.BacklogLocation");
            backlogLocationEnd.setTarget(factory.createTypeAccess(backlogLocationRef));
            CtFieldReference fieldRef = factory.Core().createFieldReference();
            fieldRef.setStatic(true);
            fieldRef.setDeclaringType(backlogLocationRef);
            fieldRef.setSimpleName("END");
            backlogLocationEnd.setVariable(fieldRef);
            
            // Add the new argument to the method call
            element.addArgument(backlogLocationEnd);
        }
    }
}