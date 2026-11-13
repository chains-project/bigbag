package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Generic transformation to fix PubNub 6.3.2 breaking changes.
 * This transformation handles three major breaking changes:
 * 1. PNConfiguration constructor change (no-arg -> requires userId/uuid)
 * 2. SubscribeCallback method renames (user -> uuid, space -> channel)
 * 3. Package structure changes for objects API
 */
public class Main {
    
    /**
     * Transformation for PNConfiguration constructor change.
     * Old: new PNConfiguration()
     * New: new PNConfiguration(new UserId("generated-uuid")) or new PNConfiguration("generated-uuid")
     * Note: Caller must handle PubNubException
     */
    public static class PNConfigurationConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        private final String pnConfigurationFqn = "com.pubnub.api.PNConfiguration";
        private final String userIdFqn = "com.pubnub.api.UserId";
        
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            // Create new constructor call with UserId parameter
            CtConstructorCall<?> newConstructorCall = getFactory().Code().createConstructorCall(
                getFactory().Type().createReference(pnConfigurationFqn),
                getFactory().Code().createConstructorCall(
                    getFactory().Type().createReference(userIdFqn),
                    getFactory().Code().createLiteral("generated-uuid")
                )
            );
            
            // Replace the old constructor call with the new one
            constructorCall.replace(newConstructorCall);
        }
        
        @Override
        public boolean isToBeProcessed(CtConstructorCall<?> candidate) {
            if (candidate.getType() == null) return false;
            return pnConfigurationFqn.equals(candidate.getType().getQualifiedName()) 
                   && candidate.getArguments().isEmpty();
        }
    }
    
    /**
     * Transformation for SubscribeCallback method renames and missing method additions.
     * Old: user(PubNub, PNUserResult) -> New: uuid(PubNub, PNUUIDMetadataResult)
     * Old: space(PubNub, PNSpaceResult) -> New: channel(PubNub, PNChannelMetadataResult)
     * Also adds missing 'file' method required by new API
     */
    public static class SubscribeCallbackMethodProcessor extends AbstractProcessor<CtMethod<?>> {
        private final String subscribeCallbackFqn = "com.pubnub.api.callbacks.SubscribeCallback";
        private final Map<String, String> methodRenames = new HashMap<>();
        private final Map<String, String> paramTypeRenames = new HashMap<>();
        
        public SubscribeCallbackMethodProcessor() {
            // Method renames
            methodRenames.put("user", "uuid");
            methodRenames.put("space", "channel");
            
            // Parameter type renames
            paramTypeRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
                               "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult");
            paramTypeRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult",
                               "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult");
        }
        
        @Override
        public void process(CtMethod<?> method) {
            String methodName = method.getSimpleName();
            if (methodRenames.containsKey(methodName)) {
                // Rename the method
                method.setSimpleName(methodRenames.get(methodName));
                
                // Update parameter types if they match old types
                method.getParameters().forEach(param -> {
                    String paramType = param.getType().getQualifiedName();
                    if (paramTypeRenames.containsKey(paramType)) {
                        param.setType(getFactory().Type().createReference(paramTypeRenames.get(paramType)));
                    }
                });
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtMethod<?> candidate) {
            // Check if method is in a class that extends/implements SubscribeCallback
            if (!(candidate.getParent() instanceof CtClass)) {
                return false;
            }
            
            CtClass<?> parentClass = (CtClass<?>) candidate.getParent();
            
            // Check superclass
            if (parentClass.getSuperclass() != null 
                && subscribeCallbackFqn.equals(parentClass.getSuperclass().getQualifiedName())) {
                return methodRenames.containsKey(candidate.getSimpleName());
            }
            
            // Check interfaces (for anonymous classes)
            for (CtTypeReference<?> iface : parentClass.getSuperInterfaces()) {
                if (subscribeCallbackFqn.equals(iface.getQualifiedName())) {
                    return methodRenames.containsKey(candidate.getSimpleName());
                }
            }
            
            return false;
        }
    }
    
    /**
     * Transformation for package structure changes.
     * Updates import statements and type references.
     */
    public static class PackageRenameProcessor extends AbstractProcessor<CtTypeReference<?>> {
        private final Map<String, String> packageRenames = new HashMap<>();
        
        public PackageRenameProcessor() {
            packageRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
                             "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult");
            packageRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult",
                             "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult");
            packageRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult",
                             "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult");
        }
        
        @Override
        public void process(CtTypeReference<?> typeRef) {
            String qualifiedName = typeRef.getQualifiedName();
            if (packageRenames.containsKey(qualifiedName)) {
                // Replace the type reference with the new one
                CtTypeReference<?> newTypeRef = getFactory().Type().createReference(packageRenames.get(qualifiedName));
                typeRef.replace(newTypeRef);
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtTypeReference<?> candidate) {
            String qualifiedName = candidate.getQualifiedName();
            return packageRenames.containsKey(qualifiedName);
        }
    }
    
    /**
     * Transformation for import statements.
     * Updates import statements to match new package structure.
     */
    public static class ImportRenameProcessor extends AbstractProcessor<CtImport> {
        private final Map<String, String> importRenames = new HashMap<>();
        
        public ImportRenameProcessor() {
            importRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNUserResult",
                            "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult");
            importRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult",
                            "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult");
            importRenames.put("com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult",
                            "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult");
        }
        
        @Override
        public void process(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String qualifiedName = typeRef.getQualifiedName();
                if (importRenames.containsKey(qualifiedName)) {
                    // Create new import with correct package
                    CtTypeReference<?> newTypeRef = getFactory().Type().createReference(importRenames.get(qualifiedName));
                    CtImport newImport = getFactory().createImport(newTypeRef);
                    ctImport.replace(newImport);
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtImport candidate) {
            if (!(candidate.getReference() instanceof CtTypeReference)) {
                return false;
            }
            CtTypeReference<?> typeRef = (CtTypeReference<?>) candidate.getReference();
            String qualifiedName = typeRef.getQualifiedName();
            return importRenames.containsKey(qualifiedName);
        }
    }
    
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java github.chains.Main <source-directory> [output-directory]");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args.length > 1 ? args[1] : sourceDir + "-transformed";
        
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(sourceDir);
        launcher.setSourceOutputDirectory(outputDir);
        
        // Add processors
        launcher.addProcessor(new PNConfigurationConstructorProcessor());
        launcher.addProcessor(new SubscribeCallbackMethodProcessor());
        launcher.addProcessor(new PackageRenameProcessor());
        launcher.addProcessor(new ImportRenameProcessor());
        
        try {
            launcher.run();
            System.out.println("Transformation completed successfully. Output written to: " + outputDir);
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}