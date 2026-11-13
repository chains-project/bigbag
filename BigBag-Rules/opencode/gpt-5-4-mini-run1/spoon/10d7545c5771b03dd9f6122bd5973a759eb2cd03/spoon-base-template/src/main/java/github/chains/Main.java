package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.Map;

public class Main {
    private static final Map<String, String> PREFIX_REWRITES = Map.of(
            "javax.ws.rs", "jakarta.ws.rs"
    );

    public static void main(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.setSourceOutputDirectory(args[1]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);

        launcher.addProcessor(new JerseyClientBuilderConstructorRewriteProcessor());
        launcher.addProcessor(new TypeReferenceRewriteProcessor());
        launcher.addProcessor(new ImportRewriteProcessor());

        launcher.buildModel();
        launcher.process();
        launcher.prettyprint();
    }

    private static String rewriteQualifiedName(String qualifiedName) {
        String match = null;
        for (String candidate : PREFIX_REWRITES.keySet()) {
            if (qualifiedName.equals(candidate) || qualifiedName.startsWith(candidate + ".")) {
                if (match == null || candidate.length() > match.length()) {
                    match = candidate;
                }
            }
        }

        if (match == null) {
            return qualifiedName;
        }

        String replacement = PREFIX_REWRITES.get(match);
        return qualifiedName.equals(match) ? replacement : replacement + qualifiedName.substring(match.length());
    }

    private static final class TypeReferenceRewriteProcessor extends AbstractProcessor<CtTypeReference<?>> {
        @Override
        public void process(CtTypeReference<?> reference) {
            String rewritten = rewriteQualifiedName(reference.getQualifiedName());
            if (rewritten.equals(reference.getQualifiedName())) {
                return;
            }

            reference.replace(reference.getFactory().Type().createReference(rewritten));
        }
    }

    private static final class JerseyClientBuilderConstructorRewriteProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> constructorCall) {
            CtTypeReference<?> type = constructorCall.getType();
            if (type == null || !"io.dropwizard.client.JerseyClientBuilder".equals(type.getQualifiedName())) {
                return;
            }

            if (constructorCall.getArguments().size() != 1) {
                return;
            }

            String source = constructorCall.getArguments().get(0).toString();
            constructorCall.replace(constructorCall.getFactory().Code().createCodeSnippetExpression(
                    "new io.dropwizard.client.JerseyClientBuilder(" + source + ".metrics())"
            ));
        }
    }

    private static final class ImportRewriteProcessor extends AbstractProcessor<CtImport> {
        @Override
        public void process(CtImport ctImport) {
            CtReference reference = ctImport.getReference();
            if (reference == null) {
                return;
            }

            CtReference replacement;
            if (reference instanceof CtTypeReference<?>) {
                String qualifiedName = ((CtTypeReference<?>) reference).getQualifiedName();
                String rewritten = rewriteQualifiedName(qualifiedName);
                if (rewritten.equals(qualifiedName)) {
                    return;
                }
                replacement = reference.getFactory().Type().createReference(rewritten);
            } else if (reference instanceof CtPackageReference) {
                String qualifiedName = ((CtPackageReference) reference).getQualifiedName();
                String rewritten = rewriteQualifiedName(qualifiedName);
                if (rewritten.equals(qualifiedName)) {
                    return;
                }
                replacement = reference.getFactory().Package().createReference(rewritten);
            } else {
                return;
            }

            ctImport.setReference(replacement);
        }
    }
}
