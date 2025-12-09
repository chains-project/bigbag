package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.factory.Factory;
import spoon.support.sniper.SniperJavaPrettyPrinter;

/**
 * Refactoring rule for the removal of com.jcabi.aspects.Tv.
 * 
 * Breaking Change:
 * - CLASS com.jcabi.aspects.Tv [REMOVED]
 * - FIELD com.jcabi.aspects.Tv.SEVEN [REMOVED]
 * 
 * Strategy:
 * - Locate usage of static field Tv.SEVEN.
 * - Replace with integer literal 7.
 * - This handles the specific field removal listed in the diff.
 */
public class TvRefactoring {

    public static class TvSevenProcessor extends AbstractProcessor<CtFieldRead<?>> {
        @Override
        public boolean isToBeProcessed(CtFieldRead<?> candidate) {
            // 1. Check if variable reference exists
            if (candidate.getVariable() == null) return false;

            // 2. Check Field Name
            // The diff explicitly lists Tv.SEVEN.
            // Note: Since the whole Tv class is removed, other fields (TEN, MILLION) might also break,
            // but we can only safely refactor what we know the value for (SEVEN -> 7).
            String fieldName = candidate.getVariable().getSimpleName();
            if (!"SEVEN".equals(fieldName)) {
                return false;
            }

            // 3. Check Declaring Type (com.jcabi.aspects.Tv)
            CtTypeReference<?> declaringType = candidate.getVariable().getDeclaringType();
            
            // Defensive checks for NoClasspath (types might be partial)
            if (declaringType == null) {
                return false; 
            }

            String qualifiedName = declaringType.getQualifiedName();
            
            // Match against "Tv" or "com.jcabi.aspects.Tv"
            // We use endsWith to handle both simple names (via imports) and FQNs.
            boolean isTvClass = qualifiedName.equals("com.jcabi.aspects.Tv") 
                             || qualifiedName.equals("Tv") 
                             || qualifiedName.endsWith(".Tv");

            return isTvClass;
        }

        @Override
        public void process(CtFieldRead<?> fieldRead) {
            Factory factory = getFactory();
            
            // Transformation: Replace Tv.SEVEN with literal 7
            // We create a literal of type int
            CtLiteral<Integer> literal = factory.Code().createLiteral(7);
            
            // Replace the field read (e.g., Tv.SEVEN) with the literal (7)
            fieldRead.replace(literal);
            
            int line = fieldRead.getPosition().isValidPosition() 
                ? fieldRead.getPosition().getLine() 
                : -1;
            System.out.println("Refactored Tv.SEVEN to 7 at line " + line);
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/54abbbde6a1233e1523a9b5f811ea100efb5dead/jcabi-ssh/src/main/java/com/jcabi/ssh/Ssh.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/54abbbde6a1233e1523a9b5f811ea100efb5dead/attempt_1/transformed";

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/54abbbde6a1233e1523a9b5f811ea100efb5dead/jcabi-ssh/src/main/java/com/jcabi/ssh/Ssh.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/54abbbde6a1233e1523a9b5f811ea100efb5dead/attempt_1/transformed");

        // CRITICAL SETTINGS for Source Preservation
        // 1. Enable comments to prevent stripping
        launcher.getEnvironment().setCommentEnabled(true);
        // 2. Force Sniper Printer manually for precise modification
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        // 3. NoClasspath mode (robustness against missing deps)
        launcher.getEnvironment().setNoClasspath(true);

        launcher.addProcessor(new TvSevenProcessor());
        
        try {
            launcher.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}