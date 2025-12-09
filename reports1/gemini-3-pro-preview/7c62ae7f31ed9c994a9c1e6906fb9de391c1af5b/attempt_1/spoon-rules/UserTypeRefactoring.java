package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtComment;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

/**
 * Spoon Refactoring Processor for Hibernate UserType Breaking Changes.
 * 
 * CHANGE ANALYSIS:
 * The diff indicates 'org.hibernate.usertype.UserType' was modified and is not source compatible.
 * In Hibernate 6 migrations, this typically refers to the signature change of 'nullSafeGet'.
 * 
 * OLD: nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
 * NEW: nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
 * 
 * STRATEGY:
 * 1. Identify methods named 'nullSafeGet' with the specific 4-argument signature found in UserType implementations.
 * 2. Update the 2nd parameter from 'String[] names' to 'int position'.
 * 3. Inject a TODO comment to warn developers that logic inside the method using the array must be manually updated.
 */
public class UserTypeRefactoring {

    public static class UserTypeProcessor extends AbstractProcessor<CtMethod<?>> {

        @Override
        public boolean isToBeProcessed(CtMethod<?> method) {
            // 1. Name Check
            if (!"nullSafeGet".equals(method.getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check (UserType.nullSafeGet has 4 arguments)
            List<CtParameter<?>> params = method.getParameters();
            if (params.size() != 4) {
                return false;
            }

            // 3. Type Check (Defensive for NoClasspath)
            // We look for the Old Signature: (ResultSet, String[], SharedSessionContractImplementor, Object)
            
            // Check Param 2: Must be String[]
            CtParameter<?> secondParam = params.get(1);
            CtTypeReference<?> secondType = secondParam.getType();
            if (secondType == null || !secondType.isArray()) {
                return false;
            }
            // Check array component type is String
            CtTypeReference<?> componentType = secondType.getArrayComponentType();
            if (componentType == null || !componentType.getQualifiedName().contains("String")) {
                return false;
            }

            // Check Param 3: Should resemble a Session implementor (heuristic for safety)
            CtParameter<?> thirdParam = params.get(2);
            CtTypeReference<?> thirdType = thirdParam.getType();
            if (thirdType == null) {
                return false; // Type unknown, safer to skip to avoid false positives on generic methods
            }
            String p3Name = thirdType.getSimpleName();
            // Matches "SharedSessionContractImplementor" or legacy "SessionImplementor"
            if (!p3Name.contains("Session") && !p3Name.contains("Implementor")) {
                return false;
            }

            return true;
        }

        @Override
        public void process(CtMethod<?> method) {
            Factory factory = getFactory();
            CtParameter<?> secondParam = method.getParameters().get(1);
            String oldParamName = secondParam.getSimpleName();

            // Transformation 1: Change type from String[] to int
            CtTypeReference<Integer> intType = factory.Type().integerPrimitiveType();
            secondParam.setType(intType);

            // Transformation 2: Rename parameter to 'position'
            secondParam.setSimpleName("position");

            // Transformation 3: Add defensive TODO comment
            // We cannot safely automate the body refactoring (names[0] vs position) without DB metadata.
            if (method.getBody() != null) {
                String msg = " TODO: Hibernate Migration: Parameter 'String[] " + oldParamName + "' changed to 'int position'. Check logic using '" + oldParamName + "'.";
                CtComment todo = factory.Code().createComment(msg, CtComment.CommentType.INLINE);
                
                // Insert at top of method body
                method.getBody().addStatement(0, todo);
            }

            CtType<?> parent = method.getDeclaringType();
            System.out.println("Refactored UserType.nullSafeGet in: " + (parent != null ? parent.getQualifiedName() : "unknown"));
        }
    }

    public static void main(String[] args) {
        // Default paths
        String inputPath = "/home/kth/Documents/last_transformer/output/7c62ae7f31ed9c994a9c1e6906fb9de391c1af5b/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/org/onebusaway/gtfs/impl/ServiceDateUserType.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7c62ae7f31ed9c994a9c1e6906fb9de391c1af5b/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/7c62ae7f31ed9c994a9c1e6906fb9de391c1af5b/onebusaway-gtfs-modules/onebusaway-gtfs-hibernate/src/main/java/org/onebusaway/gtfs/impl/ServiceDateUserType.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/7c62ae7f31ed9c994a9c1e6906fb9de391c1af5b/attempt_1/transformed");

        // CRITICAL: Preserve formatting and comments
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        
        // CRITICAL: Handle missing libraries gracefully
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new UserTypeProcessor());

        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}