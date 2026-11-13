package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
  private static final String OLD_SESSION_TYPE = "org.hibernate.engine.spi.SessionImplementor";
  private static final String NEW_SESSION_TYPE = "org.hibernate.engine.spi.SharedSessionContractImplementor";

  public static void main(String[] args) {
    Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.addInputResource(sourceRoot.toString());
    launcher.setSourceOutputDirectory(sourceRoot.toFile());
    launcher.buildModel();

    CtTypeReference<?> newSessionType = launcher.getFactory().Type().createReference(NEW_SESSION_TYPE);

    for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
      if (!"nullSafeGet".equals(method.getSimpleName()) && !"nullSafeSet".equals(method.getSimpleName())) {
        continue;
      }

      List<CtParameter<?>> parameters = method.getParameters();
      if ("nullSafeGet".equals(method.getSimpleName()) && parameters.size() >= 3) {
        replaceTypeIfNeeded(parameters.get(2), newSessionType);
      }

      if ("nullSafeSet".equals(method.getSimpleName()) && parameters.size() >= 4) {
        replaceTypeIfNeeded(parameters.get(3), newSessionType);
      }
    }

    launcher.prettyprint();
  }

  private static void replaceTypeIfNeeded(CtParameter<?> parameter, CtTypeReference<?> replacement) {
    CtTypeReference<?> currentType = parameter.getType();
    if (currentType != null && OLD_SESSION_TYPE.equals(currentType.getQualifiedName())) {
      parameter.setType(replacement.clone());
    }
  }
}
