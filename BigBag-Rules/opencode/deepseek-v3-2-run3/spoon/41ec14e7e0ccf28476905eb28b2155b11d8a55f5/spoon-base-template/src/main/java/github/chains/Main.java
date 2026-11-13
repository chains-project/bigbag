package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.factory.CodeFactory;
import spoon.reflect.factory.Factory;

public class Main {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: java github.chains.Main <sourceDir> <outputDir>");
            System.err.println("Example: java github.chains.Main /path/to/project/src /path/to/transformed/src");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        String outputDir = args[1];
        
        System.out.println("Transforming project: " + sourceDir);
        System.out.println("Output directory: " + outputDir);
        
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(sourceDir);
            launcher.setSourceOutputDirectory(outputDir);
            
            // Add processors
            launcher.addProcessor(new PropertyUtilsReplacementProcessor());
            launcher.addProcessor(new ImportCleanupProcessor());
            
            launcher.run();
            
            System.out.println("Transformation completed successfully!");
            System.out.println("Summary of changes:");
            System.out.println("1. Replaced PropertyUtils.getPropertyDescriptors with Introspector.getBeanInfo().getPropertyDescriptors()");
            System.out.println("2. Replaced PropertyUtils.getPropertyDescriptor with PropertyUtilsHelper.getPropertyDescriptor()");
            System.out.println("3. Removed commons-beanutils imports");
            System.out.println("");
            System.out.println("Note: javax.validation imports were preserved.");
            System.out.println("Projects must add javax.validation:validation-api dependency.");
            
        } catch (Exception e) {
            System.err.println("Error during transformation: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    /**
     * Processor to replace PropertyUtils method calls
     */
    static class PropertyUtilsReplacementProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            
            if (declaringType != null && 
                "org.apache.commons.beanutils.PropertyUtils".equals(declaringType.getQualifiedName())) {
                
                String methodName = invocation.getExecutable().getSimpleName();
                Factory factory = getFactory();
                
                try {
                    if ("getPropertyDescriptors".equals(methodName) && 
                        invocation.getArguments().size() == 1) {
                        // PropertyUtils.getPropertyDescriptors(Class) ->
                        // Introspector.getBeanInfo(Class).getPropertyDescriptors()
                        
                        CtExpression<?> classArg = invocation.getArguments().get(0);
                        
                        // Create Introspector.getBeanInfo(classArg)
                        CtTypeReference introspectorRef = factory.createCtTypeReference("java.beans.Introspector");
                        CtInvocation<?> getBeanInfoCall = factory.createInvocation(
                            null,
                            factory.createExecutableReference(introspectorRef, false, null, "getBeanInfo"),
                            classArg
                        );
                        
                        // Create .getPropertyDescriptors() on the result
                        CtInvocation<?> replacement = factory.createInvocation(
                            getBeanInfoCall,
                            factory.createExecutableReference(
                                factory.createArrayReference(factory.createCtTypeReference("java.beans.PropertyDescriptor")),
                                false,
                                null,
                                "getPropertyDescriptors"
                            )
                        );
                        
                        invocation.replace(replacement);
                        System.out.println("Replaced PropertyUtils.getPropertyDescriptors at: " + invocation.getPosition());
                        
                    } else if ("getPropertyDescriptor".equals(methodName) && 
                               invocation.getArguments().size() == 2) {
                        // PropertyUtils.getPropertyDescriptor(Object, String) ->
                        // PropertyUtilsHelper.getPropertyDescriptor(Object, String)
                        
                        CtExpression<?> objArg = invocation.getArguments().get(0);
                        CtExpression<?> propArg = invocation.getArguments().get(1);
                        
                        // Create PropertyUtilsHelper.getPropertyDescriptor(objArg, propArg)
                        CtTypeReference helperRef = factory.createCtTypeReference("github.chains.PropertyUtilsHelper");
                        CtInvocation<?> replacement = factory.createInvocation(
                            null,
                            factory.createExecutableReference(
                                helperRef,
                                false,
                                null,
                                "getPropertyDescriptor",
                                factory.createCtTypeReference("java.beans.PropertyDescriptor")
                            ),
                            objArg, propArg
                        );
                        
                        invocation.replace(replacement);
                        System.out.println("Replaced PropertyUtils.getPropertyDescriptor at: " + invocation.getPosition());
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error replacing " + methodName + ": " + e.getMessage());
                }
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtInvocation<?> element) {
            CtTypeReference<?> declaringType = element.getExecutable().getDeclaringType();
            return declaringType != null && 
                   "org.apache.commons.beanutils.PropertyUtils".equals(declaringType.getQualifiedName());
        }
    }
    
    /**
     * Processor to remove commons-beanutils imports
     */
    static class ImportCleanupProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport imp) {
            String importStr = imp.getReference().toString();
            if (importStr.startsWith("org.apache.commons.beanutils")) {
                System.out.println("Removing commons-beanutils import: " + importStr);
                imp.delete();
            }
        }
        
        @Override
        public boolean isToBeProcessed(CtImport element) {
            String importStr = element.getReference().toString();
            return importStr.startsWith("org.apache.commons.beanutils");
        }
    }
}