package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.Name;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
  private static final String OLD_METADATA = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_METADATA = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String SINGLE_PARTITION_BUILDER =
      "com.google.cloud.pubsublite.internal.wire.SinglePartitionPublisherBuilder";

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: Main <source-root>");
    }
    Path root = Paths.get(args[0]);
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Not a directory: " + root);
    }

    JavaParser parser = new JavaParser(new ParserConfiguration());
    List<Path> javaFiles = new ArrayList<>();
    try (var stream = Files.walk(root)) {
      stream.filter(path -> path.toString().endsWith(".java")).forEach(javaFiles::add);
    }

    for (Path file : javaFiles) {
      transformFile(parser, file);
    }
  }

  private static void transformFile(JavaParser parser, Path file) throws IOException {
    CompilationUnit cu = parser.parse(file).getResult().orElse(null);
    if (cu == null) {
      return;
    }
    boolean changed = false;

    for (ImportDeclaration importDeclaration : cu.getImports()) {
      if (OLD_METADATA.equals(importDeclaration.getNameAsString())) {
        importDeclaration.setName(new Name(NEW_METADATA));
        changed = true;
      }
    }

    for (Type type : cu.findAll(Type.class)) {
      if (type.isClassOrInterfaceType()) {
        ClassOrInterfaceType classType = type.asClassOrInterfaceType();
        if ("PublishMetadata".equals(classType.getNameAsString())) {
          classType.setName("MessageMetadata");
          changed = true;
        }
      }
    }

    for (MethodCallExpr methodCall : cu.findAll(MethodCallExpr.class)) {
      if ("setContext".equals(methodCall.getNameAsString())
          && isSinglePartitionPublisherBuilderChain(methodCall)) {
        methodCall.getScope().ifPresent(scope -> methodCall.replace(scope));
        changed = true;
      }
    }

    if (changed) {
      String output = cu.toString().replace(OLD_METADATA, NEW_METADATA);
      Files.writeString(file, output, StandardCharsets.UTF_8);
    }
  }

  private static boolean isSinglePartitionPublisherBuilderChain(MethodCallExpr methodCall) {
    Expression scope = methodCall.getScope().orElse(null);
    if (scope == null) {
      return false;
    }

    while (scope instanceof MethodCallExpr) {
      MethodCallExpr scopeCall = (MethodCallExpr) scope;
      if (!scopeCall.getScope().isPresent()) {
        return isSinglePartitionPublisherBuilderRoot(scopeCall);
      }
      scope = scopeCall.getScope().get();
    }
    return isSinglePartitionPublisherBuilderRoot(scope);
  }

  private static boolean isSinglePartitionPublisherBuilderRoot(Expression expression) {
    String text = expression.toString();
    return text.endsWith("SinglePartitionPublisherBuilder")
        || text.equals(SINGLE_PARTITION_BUILDER)
        || text.endsWith("." + SINGLE_PARTITION_BUILDER);
  }
}
