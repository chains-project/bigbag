package github.chains;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
  private static final String FLYWAY_TYPE = "org.flywaydb.core.Flyway";
  private static final String FLYWAY_CONFIGURE = "org.flywaydb.core.Flyway.configure";

  private static final Map<String, String> SETTER_TO_FLUENT = Map.of(
      "setDataSource", "dataSource",
      "setLocations", "locations",
      "setLocationsAsStrings", "locations",
      "setValidateOnMigrate", "validateOnMigrate");

  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      throw new IllegalArgumentException("Expected an input source directory path");
    }

    Path input = Paths.get(args[0]).toAbsolutePath().normalize();
    Path output = args.length >= 2
        ? Paths.get(args[1]).toAbsolutePath().normalize()
        : input.resolveSibling(input.getFileName() + "-transformed");

    Files.createDirectories(output);

    Launcher launcher = new Launcher();
    List<Path> roots = discoverSourceRoots(input);
    if (roots.isEmpty()) {
      launcher.addInputResource(input.toString());
    } else {
      for (Path root : roots) {
        launcher.addInputResource(root.toString());
      }
    }
    launcher.setSourceOutputDirectory(output.toFile());
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(true);
    launcher.buildModel();

    Factory factory = launcher.getFactory();
    List<CtLocalVariable<?>> locals = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtLocalVariable.class)));
    for (CtLocalVariable<?> local : locals) {
      if (!isFlywayType(local.getType())) {
        continue;
      }
      if (local.getDefaultExpression() == null || !isFlywayNoArgConstructor(local.getDefaultExpression())) {
        continue;
      }

      CtBlock<?> block = local.getParent(CtBlock.class);
      if (block == null) {
        local.setDefaultExpression(factory.Code().createCodeSnippetExpression(FLYWAY_CONFIGURE + ".load()"));
        continue;
      }

      List<CtStatement> statements = new ArrayList<>(block.getStatements());
      int localIndex = statements.indexOf(local);
      if (localIndex < 0) {
        local.setDefaultExpression(factory.Code().createCodeSnippetExpression(FLYWAY_CONFIGURE + ".load()"));
        continue;
      }

      Map<Integer, String> chainByIndex = new HashMap<>();
      String classLoaderArg = null;
      for (int i = localIndex + 1; i < statements.size(); i++) {
        CtStatement statement = statements.get(i);
        if (!(statement instanceof CtInvocation)) {
          continue;
        }
        CtInvocation<?> invocation = (CtInvocation<?>) statement;
        if (!isInvocationOnLocal(invocation, local)) {
          continue;
        }
        String methodName = invocation.getExecutable().getSimpleName();
        if ("setClassLoader".equals(methodName) && !invocation.getArguments().isEmpty()) {
          classLoaderArg = invocation.getArguments().get(0).toString();
          statements.get(i).delete();
          continue;
        }
        String fluentName = SETTER_TO_FLUENT.get(methodName);
        if (fluentName == null || invocation.getArguments().isEmpty()) {
          continue;
        }
        chainByIndex.put(i, "." + fluentName + "(" + joinArguments(invocation) + ")");
      }

      StringBuilder replacement = new StringBuilder(FLYWAY_CONFIGURE);
      replacement.append(classLoaderArg != null ? "(" + classLoaderArg + ")" : "()");
      for (int i = localIndex + 1; i < statements.size(); i++) {
        if (chainByIndex.containsKey(i)) {
          replacement.append(chainByIndex.get(i));
          statements.get(i).delete();
        }
      }
      replacement.append(".load()");
      local.setDefaultExpression(factory.Code().createCodeSnippetExpression(replacement.toString()));
    }

    launcher.prettyprint();
  }

  private static List<Path> discoverSourceRoots(Path input) throws Exception {
    List<Path> roots = new ArrayList<>();
    if (Files.isDirectory(input.resolve("src/main/java"))) {
      roots.add(input.resolve("src/main/java"));
    }
    if (Files.isDirectory(input.resolve("src/test/java"))) {
      roots.add(input.resolve("src/test/java"));
    }
    if (!roots.isEmpty()) {
      return roots;
    }
    try (var stream = Files.walk(input)) {
      return stream
          .filter(p -> Files.isDirectory(p) && (p.endsWith("src/main/java") || p.endsWith("src/test/java")))
          .collect(Collectors.toList());
    }
  }

  private static boolean isFlywayType(CtTypeReference<?> type) {
    return type != null && FLYWAY_TYPE.equals(type.getQualifiedName());
  }

  private static boolean isFlywayNoArgConstructor(CtExpression<?> expression) {
    if (!(expression instanceof spoon.reflect.code.CtConstructorCall)) {
      return false;
    }
    spoon.reflect.code.CtConstructorCall<?> call = (spoon.reflect.code.CtConstructorCall<?>) expression;
    return isFlywayType(call.getType()) && call.getArguments().isEmpty();
  }

  private static boolean isInvocationOnLocal(CtInvocation<?> invocation, CtLocalVariable<?> local) {
    CtExpression<?> target = invocation.getTarget();
    if (!(target instanceof CtVariableRead)) {
      return false;
    }
    CtVariableRead<?> read = (CtVariableRead<?>) target;
    return read.getVariable() != null && local.getSimpleName().equals(read.getVariable().getSimpleName());
  }

  private static String joinArguments(CtInvocation<?> invocation) {
    List<String> args = new ArrayList<>();
    for (CtExpression<?> argument : invocation.getArguments()) {
      args.add(argument.toString());
    }
    return String.join(", ", args);
  }
}
