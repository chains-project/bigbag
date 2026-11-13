package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

import java.util.Collections;

public class Main {
    private static final String TARGET_TYPE = "org.snmp4j.agent.ManagedObject";

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);

        launcher.addProcessor(new AbstractProcessor<CtType<?>>() {
            @Override
            public void process(CtType<?> type) {
                for (CtTypeReference<?> reference : type.getElements((CtTypeReference<?> ref) -> TARGET_TYPE.equals(ref.getQualifiedName()))) {
                    if (reference.getActualTypeArguments().isEmpty()) {
                        CtWildcardReference wildcard = getFactory().createWildcardReference();
                        reference.setActualTypeArguments(Collections.singletonList(wildcard));
                    }
                }
            }
        });

        launcher.run();
    }
}
