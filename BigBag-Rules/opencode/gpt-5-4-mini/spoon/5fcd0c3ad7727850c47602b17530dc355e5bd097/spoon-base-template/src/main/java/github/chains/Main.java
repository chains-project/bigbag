package github.chains;

import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

  private static final String OLD_OWNER = "org.pitest.coverage.CoverageDatabase";
  private static final String OLD_METHOD = "getClassInfo";

  public static void main(String[] args) {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
    }

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.addInputResource(args[0]);
    launcher.setSourceOutputDirectory(args[1]);
    launcher.buildModel();

    Factory factory = launcher.getFactory();
    List<CtInvocation<?>> invocations = factory.getModel().getElements(new TypeFilter<>(CtInvocation.class));
    for (CtInvocation<?> invocation : invocations) {
      if (!isOldGetClassInfoCall(invocation)) {
        continue;
      }
      if (invocation.getArguments().size() != 1) {
        continue;
      }
      CtExpression<?> replacement = invocation.getArguments().get(0).clone();
      CtElement parent = invocation.getParent();
      invocation.replace(replacement);
      if (parent instanceof CtElement) {
        ((CtElement) parent).putMetadata("changed-by-spoon", Boolean.TRUE);
      }
    }

    launcher.prettyprint();
  }

  private static boolean isOldGetClassInfoCall(CtInvocation<?> invocation) {
    CtExecutableReference<?> executable = invocation.getExecutable();
    return executable != null
        && OLD_METHOD.equals(executable.getSimpleName())
        && executable.getDeclaringType() != null
        && OLD_OWNER.equals(executable.getDeclaringType().getQualifiedName());
  }
}
