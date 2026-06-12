package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.LinkedHashSet;
import java.util.Set;

public class Main {
  private static final String REPRESENTER = "org.yaml.snakeyaml.representer.Representer";
  private static final String DUMPER_OPTIONS = "org.yaml.snakeyaml.DumperOptions";
  private static final String INTROSPECTION_EXCEPTION = "java.beans.IntrospectionException";

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Expected input and output source directories");
    }

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.addInputResource(args[0]);
    launcher.setSourceOutputDirectory(args[1]);
    launcher.buildModel();

    for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
      if (!isSnakeYamlRepresenterOverride(method)) {
        continue;
      }
      Set<CtTypeReference<? extends Throwable>> kept = new LinkedHashSet<>();
      for (CtTypeReference<? extends Throwable> thrownType : method.getThrownTypes()) {
        if (!INTROSPECTION_EXCEPTION.equals(thrownType.getQualifiedName())) {
          kept.add(thrownType);
        }
      }
      method.setThrownTypes(kept);
    }

    for (CtLocalVariable<?> local : launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class))) {
      if (local.getType() == null || !DUMPER_OPTIONS.equals(local.getType().getQualifiedName())) {
        continue;
      }
      if (!(local.getDefaultExpression() instanceof CtConstructorCall)) {
        continue;
      }
      CtConstructorCall<?> call = (CtConstructorCall<?>) local.getDefaultExpression();
      if (call.getType() == null || !DUMPER_OPTIONS.equals(call.getType().getQualifiedName())) {
        continue;
      }
      local.insertAfter(local.getFactory().Code().createCodeSnippetStatement(
          local.getSimpleName() + ".setDefaultScalarStyle(org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN);"));
    }

    launcher.prettyprint();
  }

  private static boolean isSnakeYamlRepresenterOverride(CtMethod<?> method) {
    if (!"getProperties".equals(method.getSimpleName()) || method.getParameters().size() != 1) {
      return false;
    }
    CtTypeReference<?> paramType = method.getParameters().get(0).getType();
    if (paramType == null || !"java.lang.Class".equals(paramType.getQualifiedName())) {
      return false;
    }

    CtType<?> type = method.getParent(CtType.class);
    if (type == null || type.getReference() == null) {
      return false;
    }
    CtTypeReference<?> current = type.getReference();
    while (current != null) {
      if (REPRESENTER.equals(current.getQualifiedName())) {
        return true;
      }
      current = current.getSuperclass();
    }
    return false;
  }
}
