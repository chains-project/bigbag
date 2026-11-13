package github.chains;

import java.io.File;
import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {
  private static final String OLD_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String OLD_FACTORY =
      "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";

  private Main() {}

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Expected input source directory path");
    }

    File input = new File(args[0]);
    File output = args.length > 1 ? new File(args[1]) : input;

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.addInputResource(input.getAbsolutePath());
    launcher.buildModel();

    Factory factory = launcher.getFactory();
    rewriteTypeReferences(launcher, factory);
    rewritePartitionPublisherLambdas(launcher, factory);

    launcher.setSourceOutputDirectory(output);
    launcher.prettyprint();
  }

  private static void rewriteTypeReferences(Launcher launcher, Factory factory) {
    launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)).forEach(ref -> {
      CtTypeReference<?> typeRef = (CtTypeReference<?>) ref;
      if (OLD_METADATA.equals(typeRef.getQualifiedName())) {
        typeRef.replace(factory.Type().createReference(NEW_METADATA));
      }
    });
  }

  private static void rewritePartitionPublisherLambdas(Launcher launcher, Factory factory) {
    launcher.getModel().getElements(new TypeFilter<>(CtLambda.class)).forEach(element -> {
      CtLambda<?> lambda = (CtLambda<?>) element;
      if (lambda.getType() != null && OLD_FACTORY.equals(lambda.getType().getQualifiedName())) {
        lambda.replace(buildAnonymousFactory(lambda, factory));
      }
    });
  }

  private static spoon.reflect.code.CtCodeSnippetExpression<?> buildAnonymousFactory(
      CtLambda<?> lambda, Factory factory) {
    String partitionType = "com.google.cloud.pubsublite.Partition";
    String publisherType = "com.google.cloud.pubsublite.internal.Publisher<"
        + NEW_METADATA
        + ">";
    String parameterName = lambda.getParameters().isEmpty()
        ? "partition"
        : lambda.getParameters().get(0).getSimpleName();
    String body = renderLambdaBody(lambda);

    String source =
        "new "
            + OLD_FACTORY
            + "() {"
            + "@Override public "
            + publisherType
            + " newPublisher(" + partitionType + " " + parameterName + ") {"
            + body
            + "}"
            + "@Override public void close() {}"
            + "}";

    return factory.Code().createCodeSnippetExpression(source);
  }

  private static String renderLambdaBody(CtLambda<?> lambda) {
    if (lambda.getExpression() != null) {
      return "return " + lambda.getExpression() + ";";
    }
    if (lambda.getBody() instanceof CtBlock) {
      CtBlock<?> block = (CtBlock<?>) lambda.getBody();
      StringBuilder body = new StringBuilder();
      for (CtStatement statement : block.getStatements()) {
        body.append(statement).append('\n');
      }
      return body.toString();
    }
    return "";
  }
}
