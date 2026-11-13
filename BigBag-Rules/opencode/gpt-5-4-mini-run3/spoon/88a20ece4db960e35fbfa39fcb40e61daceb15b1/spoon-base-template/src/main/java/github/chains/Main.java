package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

  private static final String OLD_METADATA_TYPE = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_METADATA_TYPE = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String PARTITION_FACTORY_TYPE =
      "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";

  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException("Usage: Main <inputDir> [outputDir]");
    }

    String inputDir = args[0];
    String outputDir = args.length == 2 ? args[1] : inputDir + "-spoon-out";

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    Path root = Path.of(inputDir);
    Path mainJava = root.resolve("src/main/java");
    Path testJava = root.resolve("src/test/java");
    if (Files.isDirectory(mainJava)) {
      launcher.addInputResource(mainJava.toString());
    }
    if (Files.isDirectory(testJava)) {
      launcher.addInputResource(testJava.toString());
    }
    if (!Files.isDirectory(mainJava) && !Files.isDirectory(testJava)) {
      launcher.addInputResource(inputDir);
    }
    launcher.setSourceOutputDirectory(outputDir);
    launcher.addProcessor(new MetadataTypeProcessor());
    launcher.addProcessor(new PartitionFactoryLambdaProcessor());
    launcher.run();
  }

  private static final class MetadataTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {
    @Override
    public void process(CtTypeReference<?> reference) {
      if (OLD_METADATA_TYPE.equals(reference.getQualifiedName())) {
        reference.replace(getFactory().Type().createReference(NEW_METADATA_TYPE));
      }
    }
  }

  private static final class PartitionFactoryLambdaProcessor extends AbstractProcessor<CtLambda<?>> {
    @Override
    public void process(CtLambda<?> lambda) {
      if (!isPartitionFactoryLambda(lambda)) {
        return;
      }

      String body = renderLambdaBody(lambda);
      String replacement = buildPartitionFactoryExpression(body);
      lambda.replace(getFactory().Code().createCodeSnippetExpression(replacement));
    }

    private boolean isPartitionFactoryLambda(CtLambda<?> lambda) {
      CtInvocation<?> invocation = lambda.getParent(CtInvocation.class);
      return invocation != null
          && "setPublisherFactory".equals(invocation.getExecutable().getSimpleName());
    }

    private String renderLambdaBody(CtLambda<?> lambda) {
      if (lambda.getExpression() != null) {
        return "return " + lambda.getExpression() + ";";
      }

      if (lambda.getBody() == null) {
        return "";
      }

      String body = lambda.getBody().toString().trim();
      if (body.startsWith("{") && body.endsWith("}")) {
        body = body.substring(1, body.length() - 1).trim();
      }
      return body;
    }

    private String buildPartitionFactoryExpression(String body) {
      return "new "
          + PARTITION_FACTORY_TYPE
          + "() {\n"
          + "  @Override\n"
          + "  public com.google.cloud.pubsublite.internal.Publisher<com.google.cloud.pubsublite.MessageMetadata> newPublisher("
          + "com.google.cloud.pubsublite.Partition partition) throws com.google.api.gax.rpc.ApiException {\n"
          + indent(body, "    ")
          + "\n"
          + "  }\n"
          + "  @Override\n"
          + "  public void close() {}\n"
          + "}";
    }

    private String indent(String text, String prefix) {
      if (text == null || text.isEmpty()) {
        return prefix + "return null;";
      }

      String[] lines = text.split("\\R", -1);
      StringBuilder builder = new StringBuilder();
      for (int i = 0; i < lines.length; i++) {
        if (i > 0) {
          builder.append('\n');
        }
        builder.append(prefix).append(lines[i]);
      }
      return builder.toString();
    }
  }
}
