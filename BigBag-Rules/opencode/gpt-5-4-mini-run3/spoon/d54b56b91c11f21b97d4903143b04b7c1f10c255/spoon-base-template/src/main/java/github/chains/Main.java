package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {
  private static final String OLD_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String OLD_CONTEXT = "com.google.cloud.pubsublite.internal.wire.PubsubContext";

  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
      System.exit(1);
    }

    Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputDir =
        args.length == 2
            ? Paths.get(args[1]).toAbsolutePath().normalize()
            : inputDir.resolveSibling(inputDir.getFileName() + "-fixed");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.getEnvironment().setCopyResources(false);
    launcher.addInputResource(inputDir.toString());
    launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
    launcher.addProcessor(new MetadataTypeProcessor());
    launcher.addProcessor(new RemoveSetContextProcessor());
    launcher.run();
  }

  private static final class MetadataTypeProcessor extends AbstractProcessor<CtTypeReference<?>> {
    @Override
    public boolean isToBeProcessed(CtTypeReference<?> typeReference) {
      return OLD_METADATA.equals(typeReference.getQualifiedName());
    }

    @Override
    public void process(CtTypeReference<?> typeReference) {
      typeReference.replace(getFactory().Type().createReference(NEW_METADATA));
    }
  }

  private static final class RemoveSetContextProcessor
      extends AbstractProcessor<CtInvocation<?>> {
    @Override
    public boolean isToBeProcessed(CtInvocation<?> invocation) {
      return "setContext".equals(invocation.getExecutable().getSimpleName());
    }

    @Override
    public void process(CtInvocation<?> invocation) {
      if (invocation.getTarget() == null) {
        return;
      }
      CtExpression<?> target = invocation.getTarget();
      invocation.replace(target);
    }
  }
}
