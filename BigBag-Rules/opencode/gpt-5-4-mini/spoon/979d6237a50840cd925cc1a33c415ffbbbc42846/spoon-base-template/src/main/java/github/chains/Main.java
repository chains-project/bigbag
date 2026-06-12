package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

public final class Main {
  private static final Map<String, String> TYPE_RENAMES = new LinkedHashMap<>();

  static {
    TYPE_RENAMES.put(
        "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter",
        "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter");
  }

  private Main() {}

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
    }

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(false);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.setSourceOutputDirectory(new File(args[1]));

    for (Path sourceFile : listJavaFiles(Path.of(args[0]))) {
      launcher.addInputResource(sourceFile.toString());
    }

    launcher.addProcessor(
        new AbstractProcessor<CtElement>() {
          @Override
          public void process(CtElement element) {
            if (!(element instanceof CtTypeAccess)) {
              return;
            }
            CtTypeAccess<?> typeAccess = (CtTypeAccess<?>) element;
            replaceIfMatched(typeAccess, typeAccess.getAccessedType());
          }

          private void replaceIfMatched(CtElement element, CtTypeReference<?> reference) {
            if (reference == null) {
              return;
            }
            String replacement = TYPE_RENAMES.get(reference.getQualifiedName());
            if (replacement == null) {
              return;
            }
            Factory factory = reference.getFactory();
            CtTypeReference<?> newReference = factory.Type().createReference(replacement);
            ((CtTypeAccess<?>) element).replace(factory.Code().createTypeAccess(newReference));
          }
        });

    launcher.run();
    rewriteGeneratedFiles(Path.of(args[1]));
  }

  private static List<Path> listJavaFiles(Path root) {
    try {
      return Files.walk(root).filter(path -> path.toString().endsWith(".java")).collect(Collectors.toList());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void rewriteGeneratedFiles(Path root) {
    for (Path file : listJavaFiles(root)) {
      try {
        String content = Files.readString(file);
        String updated = content;
        for (Map.Entry<String, String> entry : TYPE_RENAMES.entrySet()) {
          updated = updated.replace(entry.getKey(), entry.getValue());
        }
        if (!updated.equals(content)) {
          Files.writeString(file, updated, StandardCharsets.UTF_8);
        }
      } catch (IOException e) {
        throw new IllegalStateException(e);
      }
    }
  }
}
