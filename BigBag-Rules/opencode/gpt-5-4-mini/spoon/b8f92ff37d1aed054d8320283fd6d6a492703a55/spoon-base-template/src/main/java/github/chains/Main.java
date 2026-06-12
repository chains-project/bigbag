package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_TYPE = "org.apache.maven.surefire.api.testset.TestListResolver";
    private static final String OLD_METHOD = "getWildcard";
    private static final String NEW_ARGUMENT = "*.class";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Expected <input-src-dir> <output-src-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addProcessor(new WildcardResolverMigrationProcessor());
        launcher.run();
    }

    public static class WildcardResolverMigrationProcessor extends AbstractProcessor<CtInvocation<?>> {
        @Override
        public boolean isToBeProcessed(CtInvocation<?> invocation) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable == null) {
                return false;
            }
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            return declaringType != null
                    && OLD_TYPE.equals(declaringType.getQualifiedName())
                    && OLD_METHOD.equals(executable.getSimpleName())
                    && invocation.getArguments().isEmpty();
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = invocation.getFactory();
            CtConstructorCall<?> replacement = factory.Core().createConstructorCall();
            replacement.setType(factory.Type().createReference(OLD_TYPE));
            CtLiteral<String> argument = factory.createLiteral(NEW_ARGUMENT);
            replacement.addArgument(argument);
            invocation.replace(replacement);
        }
    }
}
