package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
    private static final String NEW_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String PARTITION_FACTORY =
            "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";
    private static final String PARTITION_TYPE = "com.google.cloud.pubsublite.Partition";
    private static final String PUBLISHER_TYPE = "com.google.cloud.pubsublite.internal.Publisher";
    private static final String API_EXCEPTION = "com.google.api.gax.rpc.ApiException";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected input source directory as the first argument");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "-spooned";

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setComplianceLevel(8);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir);
        launcher.setSourceOutputDirectory(new File(outputDir));

        launcher.addProcessor(new AbstractProcessor<CtTypeReference<?>>() {
            @Override
            public void process(CtTypeReference<?> reference) {
                if (OLD_METADATA.equals(reference.getQualifiedName())) {
                    reference.replace(getFactory().Type().createReference(NEW_METADATA));
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtImport>() {
            @Override
            public void process(CtImport ctImport) {
                if (ctImport.getReference() instanceof CtTypeReference) {
                    CtTypeReference<?> reference = (CtTypeReference<?>) ctImport.getReference();
                    if (OLD_METADATA.equals(reference.getQualifiedName())) {
                        ctImport.delete();
                    }
                } else if (ctImport.toString().contains(OLD_METADATA)) {
                    ctImport.delete();
                }
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (invocation == null
                        || invocation.getExecutable() == null
                        || !"setPublisherFactory".equals(invocation.getExecutable().getSimpleName())
                        || invocation.getArguments().size() != 1
                        || !(invocation.getArguments().get(0) instanceof CtLambda)) {
                    return;
                }

                CtLambda<?> lambda = (CtLambda<?>) invocation.getArguments().get(0);

                if (lambda.getParameters().size() != 1) {
                    return;
                }

                String body = renderLambdaBody(lambda);
                String parameterName = lambda.getParameters().get(0).getSimpleName();
                if (parameterName == null || parameterName.isEmpty()) {
                    parameterName = "partition";
                }

                String anonymousClass =
                        "new "
                                + PARTITION_FACTORY
                                + "() {"
                                + "@Override public void close() {} "
                                + "@Override public "
                                + PUBLISHER_TYPE
                                + " newPublisher("
                                + PARTITION_TYPE
                                + " "
                                + parameterName
                                + ") throws "
                                + API_EXCEPTION
                                + " "
                                + body
                                + " }";

                invocation.getArguments().set(0, getFactory().Code().createCodeSnippetExpression(anonymousClass));
            }
        });

        launcher.addProcessor(new AbstractProcessor<CtInvocation<?>>() {
            @Override
            public void process(CtInvocation<?> invocation) {
                if (invocation == null
                        || invocation.getExecutable() == null
                        || !"setContext".equals(invocation.getExecutable().getSimpleName())) {
                    return;
                }

                if (invocation.getTarget() != null) {
                    invocation.replace(invocation.getTarget());
                } else {
                    invocation.delete();
                }
            }
        });

        launcher.run();
    }

    private static String renderLambdaBody(CtLambda<?> lambda) {
        if (lambda.getExpression() != null) {
            return "{ return " + lambda.getExpression() + "; }";
        }
        if (lambda.getBody() == null) {
            return "{}";
        }
        return lambda.getBody().toString();
    }
}
