package github.chains;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

  private static final String LOCATION_LITERAL = "global";

  private static final Map<String, MethodRule> RULES = new LinkedHashMap<>();

  static {
    addRule("com.google.api.services.dns.Dns.ManagedZones", "create", 2);
    addRule("com.google.api.services.dns.Dns.ManagedZones", "get", 2);
    addRule("com.google.api.services.dns.Dns.ManagedZones", "list", 1);
    addRule("com.google.api.services.dns.Dns.ManagedZones", "delete", 2);
    addRule("com.google.api.services.dns.Dns.ResourceRecordSets", "list", 2);
    addRule("com.google.api.services.dns.Dns.Projects", "get", 1);
    addRule("com.google.api.services.dns.Dns.Changes", "create", 3);
    addRule("com.google.api.services.dns.Dns.Changes", "get", 3);
    addRule("com.google.api.services.dns.Dns.Changes", "list", 2);
  }

  private static void addRule(String typeName, String methodName, int oldArity) {
    RULES.put(typeName + "#" + methodName, new MethodRule(typeName, methodName, oldArity));
  }

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
      System.exit(1);
    }

    Path inputDir = Path.of(args[0]);
    Path outputDir = Path.of(args[1]);

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(true);
    launcher.getEnvironment().setPreserveLineNumbers(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());
    launcher.addProcessor(new DnsLocationInserter());
    launcher.run();
  }

  private static final class DnsLocationInserter extends AbstractProcessor<CtInvocation<?>> {

    @Override
    public void process(CtInvocation<?> invocation) {
      CtExecutableReference<?> executable = invocation.getExecutable();
      if (executable == null || invocation.getArguments().size() == 0) {
        return;
      }

      CtTypeReference<?> declaringType = executable.getDeclaringType();
      String declaringTypeName = declaringType == null ? "" : declaringType.getQualifiedName();
      MethodRule rule = null;
      for (MethodRule candidate : RULES.values()) {
        if (candidate.matches(declaringTypeName, executable.getSimpleName(), invocation.getArguments().size())) {
          rule = candidate;
          break;
        }
      }

      if (rule == null) {
        return;
      }

      invocation.addArgumentAt(1, getFactory().Code().createLiteral(LOCATION_LITERAL));
    }
  }

  private static final class MethodRule {
    private final String typeName;
    private final String methodName;
    private final int oldArity;

    private MethodRule(String typeName, String methodName, int oldArity) {
      this.typeName = typeName;
      this.methodName = methodName;
      this.oldArity = oldArity;
    }

    private boolean matches(String actualTypeName, String actualMethodName, int actualArity) {
      return actualMethodName.equals(methodName)
          && actualArity == oldArity
          && actualTypeName.equals(typeName);
    }
  }
}
