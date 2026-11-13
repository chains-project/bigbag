package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class Main {
  private static final String OLD_TYPE = "java.util.Date";
  private static final String NEW_TYPE = "java.sql.Timestamp";
  private static final String TARGET_SUPER_TYPE = "net.datafaker.DateAndTime";
  private static final String TARGET_METHOD = "between";

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
    }

    Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
    Path outputDir = args.length > 1 ? Paths.get(args[1]).toAbsolutePath().normalize() : inputDir;

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());

    launcher.addProcessor(
        new AbstractProcessor<CtMethod<?>>() {
          @Override
          public void process(CtMethod<?> method) {
            if (!TARGET_METHOD.equals(method.getSimpleName())) {
              return;
            }

            CtType<?> declaringType = method.getDeclaringType();
            if (declaringType == null) {
              return;
            }

            if (!Main.hasSuperclass(declaringType, TARGET_SUPER_TYPE)) {
              return;
            }

            for (var parameter : method.getParameters()) {
              CtTypeReference<?> type = parameter.getType();
              if (type != null && OLD_TYPE.equals(type.getQualifiedName())) {
                parameter.setType(method.getFactory().Type().createReference(NEW_TYPE));
              }
            }
          }
        });

    launcher.run();
  }

  private static boolean hasSuperclass(CtType<?> type, String qualifiedName) {
    CtTypeReference<?> current = type.getSuperclass();
    while (current != null) {
      if (qualifiedName.equals(current.getQualifiedName())) {
        return true;
      }
      current = current.getSuperclass();
    }
    return false;
  }
}
