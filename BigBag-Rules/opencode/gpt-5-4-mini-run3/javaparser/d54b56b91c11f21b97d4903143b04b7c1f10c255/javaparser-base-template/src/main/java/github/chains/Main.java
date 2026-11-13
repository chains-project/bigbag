package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {
  private static final String OLD_METADATA_IMPORT = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_METADATA_IMPORT = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String REMOVED_BUILDER_METHOD = "setContext";

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected exactly one argument: source root path");
    }

    Path sourceRoot = Paths.get(args[0]);
    if (!Files.isDirectory(sourceRoot)) {
      throw new IllegalArgumentException("Not a directory: " + sourceRoot);
    }

    try (Stream<Path> paths = Files.walk(sourceRoot)) {
      paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
          .sorted(Comparator.naturalOrder())
          .forEach(Main::rewriteFile);
    }
  }

  private static void rewriteFile(Path file) {
    try {
      String source = Files.readString(file, StandardCharsets.UTF_8);
      CompilationUnit unit = StaticJavaParser.parse(source);

      boolean changed = false;
      changed |= replaceMetadataTypes(unit);
      changed |= removeDeprecatedBuilderCalls(unit);

      if (changed) {
        String rewritten = unit.toString();
        rewritten = rewritten.replace("com.google.cloud.pubsublite.PublishMetadata", NEW_METADATA_IMPORT);
        rewritten = rewritten.replaceAll("\\bPublishMetadata\\b", "MessageMetadata");
        Files.writeString(file, rewritten, StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to rewrite " + file, e);
    }
  }

  private static boolean replaceMetadataTypes(CompilationUnit unit) {
    boolean changed = false;

    for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
      if (isTypeNamed(type, "PublishMetadata")) {
        type.setName("MessageMetadata");
        changed = true;
      }
    }

    for (var importDecl : unit.getImports()) {
      if (importDecl.getNameAsString().equals(OLD_METADATA_IMPORT)) {
        importDecl.setName(NEW_METADATA_IMPORT);
        changed = true;
      }
    }

    return changed;
  }

  private static boolean removeDeprecatedBuilderCalls(CompilationUnit unit) {
    boolean changed = false;
    List<MethodCallExpr> calls = new ArrayList<>(unit.findAll(MethodCallExpr.class));
    for (MethodCallExpr call : calls) {
      if (!call.getNameAsString().equals(REMOVED_BUILDER_METHOD)) {
        continue;
      }
      if (call.getArguments().size() != 1) {
        continue;
      }
      Optional<com.github.javaparser.ast.expr.Expression> scope = call.getScope();
      if (scope.isPresent()) {
        call.replace(scope.get().clone());
        changed = true;
      }
    }
    return changed;
  }

  private static boolean isTypeNamed(ClassOrInterfaceType type, String simpleName) {
    return type.getNameAsString().equals(simpleName);
  }
}
