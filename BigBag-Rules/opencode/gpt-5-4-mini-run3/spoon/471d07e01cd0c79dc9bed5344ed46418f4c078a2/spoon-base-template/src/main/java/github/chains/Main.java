package github.chains;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
  private static final String OLD_PACKAGE_PREFIX = "com.google.api.services.cloudresourcemanager";
  private static final String NEW_PACKAGE_PREFIX = "com.google.api.services.cloudresourcemanager.v3";

  public static void main(String[] args) {
    if (args.length < 1 || args.length > 2) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
    }

    Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputDir = args.length == 2
        ? Paths.get(args[1]).toAbsolutePath().normalize()
        : inputDir;

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(new File(outputDir.toString()));
    launcher.addProcessor(new CloudResourceManagerPackageRewriteProcessor());
    launcher.run();
  }

  private static final class CloudResourceManagerPackageRewriteProcessor
      extends AbstractProcessor<CtElement> {

    @Override
    public boolean isToBeProcessed(CtElement candidate) {
      return candidate instanceof CtTypeReference;
    }

    @Override
    public void process(CtElement element) {
      if (element instanceof CtTypeReference) {
        rewriteTypeReference((CtTypeReference<?>) element);
      }
    }

    private void rewriteTypeReference(CtTypeReference<?> reference) {
      String qualifiedName = reference.getQualifiedName();
      String rewritten = rewrite(qualifiedName);
      if (rewritten.equals(qualifiedName)) {
        return;
      }
      CtTypeReference<?> replacement = reference.getFactory().createReference(rewritten);
      reference.replace(replacement);
    }

    private String rewrite(String qualifiedName) {
      if (qualifiedName.equals(OLD_PACKAGE_PREFIX)) {
        return NEW_PACKAGE_PREFIX;
      }
      if (qualifiedName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
        return NEW_PACKAGE_PREFIX + qualifiedName.substring(OLD_PACKAGE_PREFIX.length());
      }
      return qualifiedName;
    }
  }
}
