package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set input source
        launcher.addInputResource("/workspace/biapi/src/main/java");
        // Set output directory to overwrite files
        launcher.setSourceOutputDirectory("/workspace/biapi/src/main/java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        // We want to avoid full reformatting if possible to keep diffs small, 
        // but Spoon usually reformats the whole file it touches.
        // We can try to set preservation options if available, but default is usually fine.

        launcher.addProcessor(new FloatCastFixProcessor());
        
        System.out.println("Starting Spoon transformation...");
        launcher.run();
        System.out.println("Spoon transformation complete.");
    }

    public static class FloatCastFixProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            // Target only ReportBuilder.java
            if (candidate.getPosition().getFile() == null) return false;
            if (!candidate.getPosition().getFile().getName().equals("ReportBuilder.java")) return false;

            CtExecutableReference<?> executable = candidate.getExecutable();
            if (executable != null && "setLineWidth".equals(executable.getSimpleName())) {
                 List<CtExpression<?>> args = candidate.getArguments();
                 if (args.size() == 1) {
                     CtExpression<?> arg = args.get(0);
                     if (arg instanceof CtInvocation) {
                         CtInvocation<?> argInv = (CtInvocation<?>) arg;
                         if ("getLineWidth".equals(argInv.getExecutable().getSimpleName())) {
                             return true;
                         }
                     }
                 }
            }
            return false;
        }

        @Override
        public void process(CtInvocation<?> element) {
            CtExpression<?> arg = element.getArguments().get(0);
            
            // Create a cast: (float) arg
            // We use CodeSnippetExpression for simplicity in noclasspath mode
            String originalArg = arg.toString();
            // Avoid double casting if it's already there (though check shouldn't trigger)
            if (originalArg.startsWith("(float)")) return;

            String newCode = "(float) " + originalArg;
            CtExpression<?> newExpr = getFactory().createCodeSnippetExpression(newCode);
            
            element.setArguments(List.of(newExpr));
            System.out.println("Fixed setLineWidth at " + element.getPosition());
        }
    }
}
