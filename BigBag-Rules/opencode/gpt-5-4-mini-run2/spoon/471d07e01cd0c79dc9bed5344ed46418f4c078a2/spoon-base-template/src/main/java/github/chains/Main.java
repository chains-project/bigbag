package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;

public class Main {
  private static final String OLD_PACKAGE_PREFIX = "com.google.api.services.cloudresourcemanager";
  private static final String NEW_PACKAGE_PREFIX = "com.google.api.services.cloudresourcemanager.v3";

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
    }

    Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputDir = args.length > 1
        ? Paths.get(args[1]).toAbsolutePath().normalize()
        : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());
    launcher.addProcessor(new CloudResourceManagerPackageMigrationProcessor());
    launcher.run();
    rewriteGeneratedSources(outputDir);
  }

  private static void rewriteGeneratedSources(Path outputDir) {
    try {
      Files.walk(outputDir)
          .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .forEach(
              path -> {
                try {
                  String content = Files.readString(path, StandardCharsets.UTF_8);
                  String updated = content.replace(OLD_PACKAGE_PREFIX, NEW_PACKAGE_PREFIX);
                  if (!content.equals(updated)) {
                    Files.writeString(path, updated, StandardCharsets.UTF_8);
                  }
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              });
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static final class CloudResourceManagerPackageMigrationProcessor
      extends AbstractProcessor<CtElement> {

    @Override
    public void process(CtElement element) {
      if (element instanceof CtImport) {
        rewriteImport((CtImport) element);
      }
      if (element instanceof CtTypeReference<?>) {
        rewriteTypeReference((CtTypeReference<?>) element);
      }
    }

    private void rewriteImport(CtImport ctImport) {
      if (ctImport.getReference() == null) {
        return;
      }
      String qualifiedName = ctImport.getReference().toString();
      if (qualifiedName == null || !qualifiedName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
        return;
      }
      ctImport.delete();
    }

    private void rewriteTypeReference(CtTypeReference<?> typeReference) {
      String qualifiedName = typeReference.getQualifiedName();
      if (qualifiedName == null || !qualifiedName.startsWith(OLD_PACKAGE_PREFIX + ".")) {
        return;
      }

      String updatedQualifiedName = NEW_PACKAGE_PREFIX + qualifiedName.substring(OLD_PACKAGE_PREFIX.length());
      int lastDot = updatedQualifiedName.lastIndexOf('.');
      if (lastDot < 0) {
        return;
      }

      typeReference.setPackage(
          typeReference.getFactory().Package().createReference(updatedQualifiedName.substring(0, lastDot)));
      typeReference.setSimpleName(updatedQualifiedName.substring(lastDot + 1));
    }
  }
}
