package github.chains;

import java.util.Collections;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtTypeReference;

public class Main {
  private static final String OLD_PUBLISH_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_MESSAGE_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String OLD_CONTEXT = "com.google.cloud.pubsublite.internal.wire.PubsubContext";

  public static void main(String[] args) {
    if (args.length != 2) {
      throw new IllegalArgumentException("Expected input and output directories");
    }

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.addInputResource(args[0]);
    launcher.setSourceOutputDirectory(args[1]);

    launcher.addProcessor(
        new AbstractProcessor<CtTypeReference<?>>() {
          @Override
          public void process(CtTypeReference<?> reference) {
            if (OLD_PUBLISH_METADATA.equals(reference.getQualifiedName())) {
              reference.replace(reference.getFactory().Type().createReference(NEW_MESSAGE_METADATA));
            }
          }
        });

    launcher.addProcessor(
        new AbstractProcessor<CtImport>() {
          @Override
          public void process(CtImport ctImport) {
            if (ctImport.getReference() instanceof CtTypeReference<?> typeReference
                && OLD_PUBLISH_METADATA.equals(typeReference.getQualifiedName())) {
              ctImport.setReference(ctImport.getFactory().Type().createReference(NEW_MESSAGE_METADATA));
            }
          }
        });

    launcher.addProcessor(
        new AbstractProcessor<CtInvocation<?>>() {
          @Override
          public void process(CtInvocation<?> invocation) {
            if (!isOldSetContextCall(invocation)) {
              return;
            }

            invocation.getExecutable().setSimpleName("setServiceClient");
            CtExpression<?> clientFactoryCall =
                invocation
                    .getFactory()
                    .Code()
                    .createCodeSnippetExpression(
                        "com.google.cloud.pubsublite.v1.PublisherServiceClient.create()");
            invocation.setArguments(Collections.singletonList(clientFactoryCall));
          }
        });

    launcher.run();
  }

  private static boolean isOldSetContextCall(CtInvocation<?> invocation) {
    if (!"setContext".equals(invocation.getExecutable().getSimpleName())) {
      return false;
    }
    if (invocation.getArguments().size() != 1) {
      return false;
    }
    CtExpression<?> argument = invocation.getArguments().get(0);
    if (!(argument instanceof CtInvocation<?> contextFactoryCall)) {
      return false;
    }
    if (!"of".equals(contextFactoryCall.getExecutable().getSimpleName())) {
      return false;
    }
    CtTypeReference<?> declaringType = contextFactoryCall.getExecutable().getDeclaringType();
    return declaringType != null && OLD_CONTEXT.equals(declaringType.getQualifiedName());
  }
}
