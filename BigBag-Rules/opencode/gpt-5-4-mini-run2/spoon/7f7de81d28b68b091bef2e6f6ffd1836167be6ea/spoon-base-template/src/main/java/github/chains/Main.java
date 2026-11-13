package github.chains;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.Collections;

public class Main {
    private static final String OLD_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("expected input source directory");
        }

        String input = args[0];
        String output = args.length > 1 ? args[1] : input + "-spooned";

        Launcher launcher = new Launcher();
        launcher.addInputResource(input);
        launcher.setSourceOutputDirectory(output);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();

        launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(element -> transform((CtTypeReference<?>) element));

        launcher.prettyprint();
    }

    private static void transform(CtTypeReference<?> reference) {
        if (!OLD_TYPE.equals(reference.getQualifiedName()) || !reference.getActualTypeArguments().isEmpty()) {
            return;
        }

        CtWildcardReference wildcard = reference.getFactory().createWildcardReference();
        reference.setActualTypeArguments(Collections.singletonList(wildcard));
    }
}
