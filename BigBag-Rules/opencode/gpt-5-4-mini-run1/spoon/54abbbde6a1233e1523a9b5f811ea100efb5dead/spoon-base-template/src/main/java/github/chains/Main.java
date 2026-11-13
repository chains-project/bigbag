package github.chains;

import java.util.HashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

    private static final String REMOVED_TYPE = "com.jcabi.aspects.Tv";

    private static final Map<String, Integer> CONSTANTS = new HashMap<String, Integer>() {
        private static final long serialVersionUID = 1L;
        {
            this.put("ZERO", 0);
            this.put("ONE", 1);
            this.put("TWO", 2);
            this.put("THREE", 3);
            this.put("FOUR", 4);
            this.put("FIVE", 5);
            this.put("SIX", 6);
            this.put("SEVEN", 7);
            this.put("EIGHT", 8);
            this.put("NINE", 9);
            this.put("TEN", 10);
            this.put("HUNDRED", 100);
            this.put("THOUSAND", 1000);
            this.put("MILLION", 1000000);
        }
    };

    private Main() {
        // utility class
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input> <output>");
        }
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.buildModel();
        for (final CtFieldAccess<?> access : launcher.getModel().getElements(
            new TypeFilter<CtFieldAccess<?>>(CtFieldAccess.class)
        )) {
            final CtFieldReference<?> field = access.getVariable();
            if (field == null) {
                continue;
            }
            final CtTypeReference<?> declaring = field.getDeclaringType();
            if (declaring == null || !REMOVED_TYPE.equals(declaring.getQualifiedName())) {
                continue;
            }
            final Integer value = CONSTANTS.get(field.getSimpleName());
            if (value == null) {
                continue;
            }
            final CtLiteral<Integer> literal = launcher.getFactory().Code().createLiteral(value);
            access.replace(literal);
        }
        launcher.prettyprint();
    }
}
