package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
  private static final String DATE_AND_TIME = "net.datafaker.DateAndTime";
  private static final String OLD_DATE = "java.util.Date";
  private static final String NEW_TIMESTAMP = "java.sql.Timestamp";

  public static void main(String[] args) {
    if (args.length == 0) {
      throw new IllegalArgumentException("Expected the input source directory as the first argument");
    }

    Path input = Paths.get(args[0]);
    Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-out");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.setSourceOutputDirectory(output.toFile());
    launcher.addInputResource(input.toString());
    launcher.buildModel();

    CtTypeReference<?> dateAndTime = launcher.getFactory().Type().createReference(DATE_AND_TIME);
    CtTypeReference<?> oldDate = launcher.getFactory().Type().createReference(OLD_DATE);
    CtTypeReference<?> newTimestamp = launcher.getFactory().Type().createReference(NEW_TIMESTAMP);

    for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
      if (!"between".equals(method.getSimpleName()) || method.getParameters().size() != 2) {
        continue;
      }

      CtType<?> declaringType = method.getDeclaringType();
      if (declaringType == null || !isSubtypeOf(declaringType, dateAndTime)) {
        continue;
      }

      if (!oldDate.equals(method.getParameters().get(0).getType())
          || !oldDate.equals(method.getParameters().get(1).getType())) {
        continue;
      }

      method.getParameters().get(0).setType(newTimestamp);
      method.getParameters().get(1).setType(newTimestamp);
      if (oldDate.equals(method.getType())) {
        method.setType(newTimestamp);
      }
    }

    launcher.prettyprint();
  }

  private static boolean isSubtypeOf(CtType<?> type, CtTypeReference<?> expectedSuperType) {
    CtTypeReference<?> superType = type.getSuperclass();
    while (superType != null) {
      if (expectedSuperType.equals(superType)) {
        return true;
      }
      CtType<?> resolved = superType.getTypeDeclaration();
      if (resolved == null) {
        return false;
      }
      superType = resolved.getSuperclass();
    }
    return false;
  }
}
