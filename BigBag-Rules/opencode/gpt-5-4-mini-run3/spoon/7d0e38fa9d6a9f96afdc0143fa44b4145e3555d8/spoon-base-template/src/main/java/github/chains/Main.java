package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

  private static final String LOCATION_LITERAL = "global";

  private static final Map<String, Integer> OLD_ARITY = new LinkedHashMap<>();

  static {
    oldArity("managedZones", "create", 2);
    oldArity("managedZones", "get", 2);
    oldArity("managedZones", "list", 1);
    oldArity("managedZones", "delete", 2);

    oldArity("resourceRecordSets", "list", 2);
    oldArity("projects", "get", 1);

    oldArity("changes", "create", 3);
    oldArity("changes", "get", 3);
    oldArity("changes", "list", 2);

    oldArity("policies", "create", 2);
    oldArity("policies", "delete", 2);
    oldArity("policies", "get", 2);
    oldArity("policies", "list", 1);
    oldArity("policies", "patch", 3);
    oldArity("policies", "update", 3);

    oldArity("responsePolicies", "create", 2);
    oldArity("responsePolicies", "delete", 2);
    oldArity("responsePolicies", "get", 2);
    oldArity("responsePolicies", "list", 1);
    oldArity("responsePolicies", "patch", 3);
    oldArity("responsePolicies", "update", 3);

    oldArity("responsePolicyRules", "create", 3);
    oldArity("responsePolicyRules", "delete", 3);
    oldArity("responsePolicyRules", "get", 3);
    oldArity("responsePolicyRules", "list", 2);
    oldArity("responsePolicyRules", "patch", 4);
    oldArity("responsePolicyRules", "update", 4);
  }

  private static void oldArity(String accessor, String method, int arity) {
    OLD_ARITY.put(accessor + "#" + method, arity);
  }

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
    }

    Path input = Paths.get(args[0]);
    Path output = args.length > 1 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

    Launcher launcher = new Launcher();
    launcher.addInputResource(input.toString());
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.getEnvironment().setSourceOutputDirectory(output.toFile());
    launcher.buildModel();

    for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
      rewrite(invocation);
    }

    launcher.prettyprint();
  }

  private static void rewrite(CtInvocation<?> invocation) {
    CtExecutableReference<?> executable = invocation.getExecutable();
    if (executable == null) {
      return;
    }

    String accessor = getAccessorName(invocation.getTarget());
    if (accessor == null) {
      return;
    }

    Integer expectedArgs = OLD_ARITY.get(accessor + "#" + executable.getSimpleName());
    if (expectedArgs == null || invocation.getArguments().size() != expectedArgs) {
      return;
    }

    List<CtExpression<?>> arguments = new ArrayList<>(invocation.getArguments());
    arguments.add(1, invocation.getFactory().Code().createLiteral(LOCATION_LITERAL));
    invocation.setArguments(arguments);
  }

  private static String getAccessorName(CtExpression<?> target) {
    if (!(target instanceof CtInvocation<?> targetInvocation)) {
      return null;
    }

    CtExecutableReference<?> targetExecutable = targetInvocation.getExecutable();
    if (targetExecutable == null) {
      return null;
    }
    String name = targetExecutable.getSimpleName();
    return OLD_ARITY.keySet().stream().anyMatch(k -> k.startsWith(name + "#")) ? name : null;
  }
}
