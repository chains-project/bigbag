package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

public class JpaRepositoryRefactoring {

    /**
     * Processor to migrate JpaRepository.getOne(ID) to JpaRepository.getReferenceById(ID).
     * 
     * Analysis:
     * The input diff indicates a breaking modification in JpaRepository.
     * In Spring Data JPA 3.x, `getOne(ID)` (which returned a reference proxy) was removed 
     * and replaced by `getReferenceById(ID)` to avoid confusion with `findById` (which returns Optional).
     */
    public static class JpaRepositoryProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // 1. Name Check
            // We are looking specifically for the old method name "getOne"
            if (!"getOne".equals(candidate.getExecutable().getSimpleName())) {
                return false;
            }

            // 2. Argument Count Check
            // getOne accepts exactly one argument (the ID)
            if (candidate.getArguments().size() != 1) {
                return false;
            }

            // 3. Type/Context Check (Defensive for NoClasspath)
            // We verify if the method belongs to a Repository.
            
            // Check A: The type of the variable calling the method (e.g., userRepository.getOne(...))
            CtExpression<?> target = candidate.getTarget();
            if (target != null) {
                CtTypeReference<?> targetType = target.getType();
                // If type is resolvable (even partially in NoClasspath), check naming convention
                if (targetType != null) {
                    String qName = targetType.getQualifiedName();
                    // Heuristic: Spring Data interfaces typically end in 'Repository' or contain 'JpaRepository'
                    if (qName.contains("JpaRepository") || qName.endsWith("Repository")) {
                        return true;
                    }
                }
            }

            // Check B: The declaring type of the method reference
            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType != null) {
                String declName = declaringType.getQualifiedName();
                if (declName.contains("JpaRepository") || declName.endsWith("Repository")) {
                    return true;
                }
            }

            // If we cannot confirm it's a repository, we skip to avoid false positives on unrelated getOne() methods.
            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            // Refactoring Action: Rename the method invocation
            String oldName = invocation.getExecutable().getSimpleName();
            
            // This updates the AST reference; Spoon's printer will output the new name
            invocation.getExecutable().setSimpleName("getReferenceById");
            
            System.out.println("Refactored " + oldName + " to getReferenceById at line " 
                + invocation.getPosition().getLine());
        }
    }

    public static void main(String[] args) {
        // Default paths (editable via args)
        String inputPath = "/home/kth/Documents/last_transformer/output/9da8825fbdb24922b94be9eb82eefc73640d8f6b/openhospital-core/src/main/java/org/isf/medicals/service/MedicalsIoOperationRepository.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9da8825fbdb24922b94be9eb82eefc73640d8f6b/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/9da8825fbdb24922b94be9eb82eefc73640d8f6b/openhospital-core/src/main/java/org/isf/medicals/service/MedicalsIoOperationRepository.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/9da8825fbdb24922b94be9eb82eefc73640d8f6b/attempt_1/transformed");

        // CRITICAL SETTINGS FOR PRESERVING CODE STRUCTURE
        // 1. Enable comments
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually to preserve formatting/indentation
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. Enable NoClasspath mode to handle partial dependencies
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new JpaRepositoryProcessor());
        
        try {
            launcher.run();
            System.out.println("Refactoring complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}