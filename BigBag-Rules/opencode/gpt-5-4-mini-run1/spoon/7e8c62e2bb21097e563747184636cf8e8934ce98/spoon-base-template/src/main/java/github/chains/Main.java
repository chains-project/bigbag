package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;

public class Main {
    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected input and output source directories");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.addProcessor(new ScriptResultImportRemovalProcessor());
        launcher.addProcessor(new ScriptResultReplacementProcessor());
        launcher.run();
    }

    private static final class ScriptResultImportRemovalProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(final CtImport ctImport) {
            if (ctImport.getReference() != null
                    && "com.gargoylesoftware.htmlunit.ScriptResult".equals(ctImport.getReference().toString())) {
                ctImport.delete();
            }
        }
    }

    private static final class ScriptResultReplacementProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public void process(final CtInvocation<?> invocation) {
            if (!"getJavaScriptResult".equals(invocation.getExecutable().getSimpleName())) {
                return;
            }

            CtExpression<?> target = invocation.getTarget();
            if (target == null) {
                return;
            }

            if (!(target instanceof CtConstructorCall<?>)) {
                return;
            }

            CtConstructorCall<?> constructorCall = (CtConstructorCall<?>) target;
            if (constructorCall.getArguments().isEmpty()) {
                return;
            }

            CtExpression<?> replacement = constructorCall.getArguments().get(0).clone();
            invocation.replace(replacement);
        }
    }
}
