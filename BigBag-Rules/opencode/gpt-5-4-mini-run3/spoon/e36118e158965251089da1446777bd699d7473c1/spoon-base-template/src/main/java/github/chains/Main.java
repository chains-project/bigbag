package github.chains;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_TYPE = "org.cactoos.map.MapEntry";

    private static final String NEW_ENTRY_FACTORY =
        "java.util.Map#entry(java.lang.Object,java.lang.Object)";

    public static void main(final String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        if (args.length > 1) {
            launcher.setSourceOutputDirectory(args[1]);
        } else {
            launcher.setSourceOutputDirectory(
                Path.of(args[0]).resolveSibling("spooned").toString()
            );
        }
        launcher.buildModel();
        final CtExecutableReference<?> entry = launcher.getFactory().Executable()
            .createReference(NEW_ENTRY_FACTORY);
        final CtTypeAccess<?> mapType = launcher.getFactory().Code().createTypeAccess(
            launcher.getFactory().Type().createReference("java.util.Map")
        );
        for (CtConstructorCall<?> call : launcher.getModel().getElements(
            new TypeFilter<>(CtConstructorCall.class)
        )) {
            if (call.getType() != null && OLD_TYPE.equals(call.getType().getQualifiedName())) {
                final List<CtExpression<?>> arguments = new ArrayList<>(call.getArguments());
                if (arguments.size() == 2) {
                    final CtInvocation<?> replacement = launcher.getFactory().Code()
                        .createInvocation(mapType, entry, arguments);
                    call.replace(replacement);
                }
            }
        }
        launcher.prettyprint();
    }
}
