package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

  private static final String OLD_METHOD_NAME = "between";
  private static final String OLD_TYPE = "java.util.Date";
  private static final String NEW_TYPE = "java.sql.Timestamp";

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Expected input and output source directories");
    }

    Path input = Paths.get(args[0]);
    Path output = Paths.get(args[1]);

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.addInputResource(input.toString());
    launcher.setSourceOutputDirectory(output.toString());
    launcher.buildModel();

    CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_TYPE);
    CtExecutableReference<?> getTimeRef = launcher.getFactory().Executable().createReference(
        launcher.getFactory().Type().createReference(java.util.Date.class),
        launcher.getFactory().Type().longPrimitiveType(),
        "getTime");

    rewriteMethodDeclarations(launcher, newType);
    rewriteInvocations(launcher, newType, getTimeRef);

    launcher.prettyprint();
  }

  private static void rewriteMethodDeclarations(Launcher launcher, CtTypeReference<?> newType) {
    for (CtMethod<?> method : launcher.getModel().getElements(new TypeFilter<>(CtMethod.class))) {
      if (!OLD_METHOD_NAME.equals(method.getSimpleName())) {
        continue;
      }
      List<CtParameter<?>> parameters = method.getParameters();
      if (parameters.size() != 2) {
        continue;
      }
      if (!isType(parameters.get(0).getType(), OLD_TYPE) || !isType(parameters.get(1).getType(), OLD_TYPE)) {
        continue;
      }
      parameters.get(0).setType(newType.clone());
      parameters.get(1).setType(newType.clone());
    }
  }

  private static void rewriteInvocations(
      Launcher launcher, CtTypeReference<?> newType, CtExecutableReference<?> getTimeRef) {
    for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
      CtExecutableReference<?> executable = invocation.getExecutable();
      if (executable == null || !OLD_METHOD_NAME.equals(executable.getSimpleName())) {
        continue;
      }
      if (invocation.getArguments().size() != 2) {
        continue;
      }
      if (!isType(invocation.getArguments().get(0).getType(), OLD_TYPE)
          || !isType(invocation.getArguments().get(1).getType(), OLD_TYPE)) {
        continue;
      }

      List<CtExpression<?>> args = invocation.getArguments();
      args.set(0, wrapDateAsTimestamp(args.get(0), newType, getTimeRef));
      args.set(1, wrapDateAsTimestamp(args.get(1), newType, getTimeRef));
    }
  }

  private static CtExpression<?> wrapDateAsTimestamp(
      CtExpression<?> expression, CtTypeReference<?> timestampType, CtExecutableReference<?> getTimeRef) {
    CtExpression<?> cloned = expression.clone();
    CtInvocation<?> getTimeInvocation = cloned.getFactory().Code().createInvocation(cloned, getTimeRef);
    return cloned.getFactory().Code().createConstructorCall(timestampType, getTimeInvocation);
  }

  private static boolean isType(CtTypeReference<?> type, String qualifiedName) {
    return type != null && qualifiedName.equals(type.getQualifiedName());
  }
}
