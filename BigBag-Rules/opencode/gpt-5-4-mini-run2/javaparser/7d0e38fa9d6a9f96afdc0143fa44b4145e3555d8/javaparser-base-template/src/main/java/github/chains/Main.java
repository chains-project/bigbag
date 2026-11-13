package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Main {
  private static final List<Rule> RULES = new ArrayList<>();

  static {
    register("changes", "create", 3);
    register("changes", "get", 3);
    register("changes", "list", 2);

    register("dnsKeys", "get", 3);
    register("dnsKeys", "list", 2);

    register("managedZoneOperations", "get", 3);
    register("managedZoneOperations", "list", 2);

    register("managedZones", "create", 2);
    register("managedZones", "delete", 2);
    register("managedZones", "get", 2);
    register("managedZones", "list", 1);
    register("managedZones", "patch", 3);
    register("managedZones", "update", 3);

    register("policies", "create", 2);
    register("policies", "delete", 2);
    register("policies", "get", 2);
    register("policies", "list", 1);
    register("policies", "patch", 3);
    register("policies", "update", 3);

    register("projects", "get", 1);

    register("resourceRecordSets", "create", 3);
    register("resourceRecordSets", "delete", 4);
    register("resourceRecordSets", "get", 4);
    register("resourceRecordSets", "list", 2);
    register("resourceRecordSets", "patch", 5);

    register("responsePolicies", "create", 2);
    register("responsePolicies", "delete", 2);
    register("responsePolicies", "get", 2);
    register("responsePolicies", "list", 1);
    register("responsePolicies", "patch", 3);
    register("responsePolicies", "update", 3);

    register("responsePolicyRules", "create", 3);
    register("responsePolicyRules", "delete", 3);
    register("responsePolicyRules", "get", 3);
    register("responsePolicyRules", "list", 2);
    register("responsePolicyRules", "patch", 4);
    register("responsePolicyRules", "update", 4);
  }

  public static void main(String[] args) throws IOException {
    Path sourceRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
    if (!Files.isDirectory(sourceRoot)) {
      throw new IllegalArgumentException("Expected a source directory: " + sourceRoot);
    }

    ParserConfiguration configuration = new ParserConfiguration();
    configuration.setCharacterEncoding(StandardCharsets.UTF_8);
    StaticJavaParser.setConfiguration(configuration);

    int modifiedFiles = 0;
    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      for (Path path : (Iterable<Path>) paths.filter(Main::isJavaSource)::iterator) {
        if (transformFile(path)) {
          modifiedFiles++;
        }
      }
    }

    System.out.println("Updated " + modifiedFiles + " file(s).");
  }

  private static boolean isJavaSource(Path path) {
    return Files.isRegularFile(path) && path.toString().endsWith(".java");
  }

  private static boolean transformFile(Path file) throws IOException {
    CompilationUnit cu = StaticJavaParser.parse(file);
    boolean changed = false;

    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      if (!call.getScope().isPresent() || call.getArguments().size() > 6) {
        continue;
      }

      for (Rule rule : RULES) {
        if (rule.matches(call)) {
          call.getArguments().add(1, new StringLiteralExpr(""));
          changed = true;
          break;
        }
      }
    }

    if (changed) {
      Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
    }
    return changed;
  }

  private static void register(String accessor, String method, int oldArity) {
    RULES.add(new Rule(accessor, method, oldArity));
  }

  private static final class Rule {
    private final String accessor;
    private final String method;
    private final int oldArity;

    private Rule(String accessor, String method, int oldArity) {
      this.accessor = accessor;
      this.method = method;
      this.oldArity = oldArity;
    }

    private boolean matches(MethodCallExpr call) {
      if (!method.equals(call.getNameAsString()) || call.getArguments().size() != oldArity) {
        return false;
      }
      return call.getScope()
          .filter(MethodCallExpr.class::isInstance)
          .map(MethodCallExpr.class::cast)
          .filter(scope -> accessor.equals(scope.getNameAsString()))
          .isPresent();
    }
  }
}
