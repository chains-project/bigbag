package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.factory.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java -cp ... github.chains.Main <source-directory>");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        System.out.println("Processing source directory: " + sourceDir);
        
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Set output directory
        launcher.setSourceOutputDirectory("/workspace/transformed-artifactory");
        
        // Add processor for StringUtils.isAllBlank transformation
        launcher.addProcessor(new StringUtilsIsAllBlankProcessor());
        // Add processor for ClientHelper constructor transformation  
        launcher.addProcessor(new ClientHelperConstructorProcessor());
        
        launcher.run();
        
        System.out.println("Transformation completed!");
    }
    
    static class StringUtilsIsAllBlankProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(CtInvocation<?> invocation) {
            try {
                // Check if this is a call to StringUtils.isAllBlank
                if (invocation.getExecutable() == null) return;
                
                CtExecutableReference<?> execRef = invocation.getExecutable();
                if (!"isAllBlank".equals(execRef.getSimpleName())) return;
                
                // For isAllBlank method, we need to check if it's from StringUtils
                // The declaring type might be null for static imports
                // So we check the method name and assume it's StringUtils.isAllBlank
                // since that's a common pattern
                if (!"isAllBlank".equals(execRef.getSimpleName())) {
                    return;
                }
                // Optional: We could also check if there's a StringUtils import in the file
                // but for simplicity, we'll assume any isAllBlank with multiple string args
                // is StringUtils.isAllBlank
                
                System.out.println("Found isAllBlank call at " + invocation.getPosition());
                
                // Get arguments
                List<CtExpression<?>> args = invocation.getArguments();
                if (args.isEmpty()) {
                    return;
                }
                
                // Build AND expression chain
                CtExpression<?> andChain = null;
                
                for (CtExpression<?> arg : args) {
                    // Create StringUtils.isBlank(arg) invocation
                    // First create executable reference for isBlank
                    CtExecutableReference<Boolean> isBlankRef = getFactory().Core().createExecutableReference();
                    isBlankRef.setDeclaringType(getFactory().Type().createReference("org.apache.commons.lang3.StringUtils"));
                    isBlankRef.setSimpleName("isBlank");
                    isBlankRef.setType(getFactory().Type().BOOLEAN);
                    
                    // Create type access for StringUtils
                    CtTypeAccess<?> typeAccess = getFactory().Code().createTypeAccess(
                        getFactory().Type().createReference("org.apache.commons.lang3.StringUtils")
                    );
                    
                    // Create invocation
                    CtInvocation<Boolean> isBlankInvocation = getFactory().Code().createInvocation(
                        typeAccess,
                        isBlankRef,
                        Arrays.asList(arg)
                    );
                    
                    // Build AND chain
                    if (andChain == null) {
                        andChain = isBlankInvocation;
                    } else {
                        CtBinaryOperator<Boolean> andOp = getFactory().Code().createBinaryOperator(
                            andChain,
                            isBlankInvocation,
                            BinaryOperatorKind.AND
                        );
                        andChain = andOp;
                    }
                }
                
                if (andChain != null) {
                    invocation.replace(andChain);
                    System.out.println("Replaced isAllBlank with AND chain");
                }
                
            } catch (Exception e) {
                System.err.println("Error processing isAllBlank: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    static class ClientHelperConstructorProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            try {
                // Check if this is a call to ClientHelper constructor with old signature
                if (constructorCall.getExecutable() == null) return;
                
                CtExecutableReference<?> execRef = constructorCall.getExecutable();
                if (!"ClientHelper".equals(execRef.getSimpleName())) return;
                
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (declaringType == null || !"org.jenkinsci.plugins.p4.client.ClientHelper".equals(declaringType.getQualifiedName())) {
                    return;
                }
                
                // Check if it matches old signature: ClientHelper(String, TaskListener, String, String)
                List<CtExpression<?>> args = constructorCall.getArguments();
                if (args.size() != 4) return;
                
                // Check first argument type (should be String for old signature)
                CtExpression<?> firstArg = args.get(0);
                CtTypeReference<?> firstArgType = firstArg.getType();
                if (firstArgType == null || !"java.lang.String".equals(firstArgType.getQualifiedName())) {
                    return;
                }
                
                // Check second argument type (should be TaskListener)
                CtExpression<?> secondArg = args.get(1);
                CtTypeReference<?> secondArgType = secondArg.getType();
                if (secondArgType == null || !"hudson.model.TaskListener".equals(secondArgType.getQualifiedName())) {
                    return;
                }
                
                System.out.println("Found old ClientHelper constructor call at " + constructorCall.getPosition());
                
                // The transformation for ClientHelper is complex because:
                // 1. Need to determine the context (Item or ItemGroup)
                // 2. Need to create Workspace object from clientString and charset
                // 3. Parameters shifted: old (credentials, listener, clientString, charset) 
                //    -> new (context, credentials, listener, workspace)
                
                // For a generic transformation, we can't easily determine the context
                // This requires project-specific analysis
                
                System.out.println("ClientHelper transformation requires manual fix:");
                System.out.println("Old: ClientHelper(" + args.get(0) + ", " + args.get(1) + ", " + args.get(2) + ", " + args.get(3) + ")");
                System.out.println("New signature: ClientHelper(context, credentialsId, listener, workspace)");
                System.out.println("Need to determine 'context' (Item/ItemGroup) and create Workspace object");
                
            } catch (Exception e) {
                System.err.println("Error processing ClientHelper: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}