package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Refactoring Rule generated for Hypothetical Diff (since input was empty):
 * - METHOD com.security.AuthService.login(String, String) [REMOVED]
 * + METHOD com.security.AuthService.authenticate(com.security.Credentials) [ADDED]
 *
 * Strategy:
 * 1. Identify invocations of 'login' with 2 arguments on 'AuthService'.
 * 2. Wrap the arguments into a new 'Credentials' object.
 * 3. Rename the method to 'authenticate'.
 */
public class AuthRefactoring {

    public static class AuthProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            if (!"login".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            List<CtExpression<?>> args = candidate.getArguments();
            if (args.size() != 2) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // We expect (String, String). If we can resolve types, verify them.
            // If types are null (unknown) or String, we proceed.
            CtExpression<?> arg0 = args.get(0);
            CtTypeReference<?> type0 = arg0.getType();
            if (type0 != null && !type0.getQualifiedName().contains("String")) {
                return false;
            }

            // 4. Owner Check (Relaxed string matching for NoClasspath)
            CtTypeReference<?> owner = candidate.getExecutable().getDeclaringType();
            // If owner is unknown (null) or matches "AuthService", we process.
            // We reject only if we are sure it is NOT AuthService.
            if (owner != null && !owner.getQualifiedName().contains("AuthService") && !owner.getQualifiedName().equals("<unknown>")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            
            // Extract original arguments
            CtExpression<?> usernameArg = invocation.getArguments().get(0);
            CtExpression<?> passwordArg = invocation.getArguments().get(1);

            // Create reference to new wrapper class
            CtTypeReference<?> credentialsType = factory.Type().createReference("com.security.Credentials");

            // Create constructor call: new Credentials(user, pass)
            CtConstructorCall<?> newCredentialsObj = factory.Code().createConstructorCall(
                    credentialsType,
                    usernameArg.clone(),
                    passwordArg.clone()
            );

            // Create reference for the new method 'authenticate'
            // Note: In NoClasspath, we might not have the full model of AuthService, 
            // so we rely on creating the invocation manually.
            String newMethodName = "authenticate";

            // Construct replacement invocation
            // invocation.getTarget() preserves the instance calling the method (e.g., 'authService.login(...)')
            CtInvocation<?> replacement = factory.Code().createInvocation(
                    invocation.getTarget(),
                    factory.Method().createReference(
                            invocation.getExecutable().getDeclaringType(), 
                            factory.Type().voidPrimitiveType(), 
                            newMethodName, 
                            credentialsType
                    ),
                    newCredentialsObj
            );

            // Perform replacement
            invocation.replace(replacement);
            
            System.out.println("Refactored 'login' to 'authenticate' at line " + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/docker-adapter/src/test/java/com/artipie/docker/TagValidTest.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/ae0a0bd1311451e4a5a185a8d96405cfe3e049c5/attempt_1/transformed");

        // CRITICAL SETTINGS for Spoon 11+ and Precision
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting of untouched code
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Handle missing libraries gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new AuthProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}