package github.chains;

import java.util.ArrayList;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String DEFAULT_SOURCE = "src";

    private static final String[] TARGET_TYPES = {
        "org.hamcrest.core.StringContains",
        "org.hamcrest.core.StringStartsWith"
    };

    public static void main(final String[] args) {
        final String source = args.length > 0 ? args[0] : DEFAULT_SOURCE;
        final Launcher launcher = new Launcher();
        launcher.addInputResource(source);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.buildModel();
        final List<CtConstructorCall<?>> calls = new ArrayList<>(launcher.getModel()
            .getElements(new TypeFilter<>(CtConstructorCall.class)));
        for (final CtConstructorCall<?> call : calls) {
            if (isTarget(call)) {
                rewrite(call);
            }
        }
        launcher.setSourceOutputDirectory(source);
        launcher.prettyprint();
    }

    private static boolean isTarget(final CtConstructorCall<?> call) {
        final CtExecutableReference<?> executable = call.getExecutable();
        if (executable == null || executable.getDeclaringType() == null) {
            return false;
        }
        final String type = executable.getDeclaringType().getQualifiedName();
        for (final String target : TARGET_TYPES) {
            if (target.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static void rewrite(final CtConstructorCall<?> call) {
        final List<CtExpression<?>> args = new ArrayList<>(call.getArguments());
        if (args.size() < 2 || !(args.get(0) instanceof CtLiteral)) {
            return;
        }
        final Object value = ((CtLiteral<?>) args.get(0)).getValue();
        if (!(value instanceof Boolean)) {
            return;
        }
        args.remove(0);
        call.setArguments(args);
    }
}
