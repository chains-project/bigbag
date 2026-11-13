package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.stmt.ReturnStmt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class Main {
  private static final String OLD_TYPE = "PublishMetadata";
  private static final String NEW_TYPE = "MessageMetadata";
  private static final String OLD_TYPE_FQN = "com.google.cloud.pubsublite.PublishMetadata";
  private static final String NEW_TYPE_FQN = "com.google.cloud.pubsublite.MessageMetadata";
  private static final String FACTORY_FQN = "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";
  private static final String PARTITION_FQN = "com.google.cloud.pubsublite.Partition";
  private static final String PUBLISHER_FQN = "com.google.cloud.pubsublite.internal.Publisher";

  public static void main(String[] args) {
    if (args.length != 1) {
      throw new IllegalArgumentException("Expected exactly one argument: source directory");
    }
    try (Stream<Path> paths = Files.walk(Paths.get(args[0]))) {
      paths.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".java"))
          .forEach(Main::transformFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void transformFile(Path path) {
    try {
      CompilationUnit cu = StaticJavaParser.parse(path);
      boolean changed = false;
      changed |= replaceOldTypeReferences(cu);
      changed |= replaceLambdaFactories(cu);
      changed |= replaceAnonymousFactoryBodies(cu);
      changed |= removeContextSetters(cu);
      if (changed) {
        Files.writeString(path, cu.toString());
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to transform " + path, e);
    }
  }

  private static boolean replaceOldTypeReferences(CompilationUnit cu) {
    boolean changed = false;
    changed |= cu.getImports().removeIf(i -> i.getNameAsString().equals(OLD_TYPE_FQN));
    if (cu.getImports().stream().noneMatch(i -> i.getNameAsString().equals(NEW_TYPE_FQN))) {
      cu.addImport(NEW_TYPE_FQN);
    }
    for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
      if (type.getNameAsString().equals(OLD_TYPE)) {
        type.setName(NEW_TYPE);
        changed = true;
      }
    }
    for (Type t : cu.findAll(Type.class)) {
      if (t.toString().equals(OLD_TYPE)) {
        t.replace(StaticJavaParser.parseType(NEW_TYPE));
        changed = true;
      }
    }
    return changed;
  }

  private static boolean replaceLambdaFactories(CompilationUnit cu) {
    boolean changed = false;
    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      if (!"setPublisherFactory".equals(call.getNameAsString()) || call.getArguments().size() != 1) {
        continue;
      }
      Expression arg = call.getArgument(0);
      if (arg.isLambdaExpr()) {
        call.setArgument(0, buildFactoryAnonymousClass(arg.asLambdaExpr().getBody().toString()));
        changed = true;
      }
    }
    return changed;
  }

  private static boolean replaceAnonymousFactoryBodies(CompilationUnit cu) {
    boolean changed = false;
    for (ObjectCreationExpr expr : cu.findAll(ObjectCreationExpr.class)) {
      if (!expr.getType().asString().endsWith("PartitionPublisherFactory")) {
        continue;
      }
      if (expr.getAnonymousClassBody().isEmpty()) {
        continue;
      }
      for (BodyDeclaration<?> member : expr.getAnonymousClassBody().get()) {
        if (member.isMethodDeclaration()) {
          if (member.asMethodDeclaration().getType().toString().contains(OLD_TYPE)) {
            member.asMethodDeclaration().setType(StaticJavaParser.parseType(NEW_TYPE_FQN));
            changed = true;
          }
          for (ReturnStmt ret : member.asMethodDeclaration().findAll(ReturnStmt.class)) {
            if (ret.getExpression().isPresent() && ret.getExpression().get().isLambdaExpr()) {
              LambdaExpr lambda = ret.getExpression().get().asLambdaExpr();
              if (lambda.getBody().isExpressionStmt()) {
                ret.setExpression(lambda.getBody().asExpressionStmt().getExpression());
              } else if (lambda.getBody().isBlockStmt()) {
                lambda.getBody().asBlockStmt().getStatements().stream()
                    .filter(stmt -> stmt.isExpressionStmt() || stmt.isReturnStmt())
                    .findFirst()
                    .ifPresent(stmt -> {
                      if (stmt.isExpressionStmt()) {
                        ret.setExpression(stmt.asExpressionStmt().getExpression());
                      } else if (stmt.asReturnStmt().getExpression().isPresent()) {
                        ret.setExpression(stmt.asReturnStmt().getExpression().get());
                      }
                    });
              }
              changed = true;
            }
          }
        }
      }
    }
    return changed;
  }

  private static boolean removeContextSetters(CompilationUnit cu) {
    boolean changed = false;
    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      if ("setContext".equals(call.getNameAsString()) && call.getArguments().size() == 1) {
        if (call.getScope().isPresent()) {
          call.replace(call.getScope().get());
        } else {
          call.remove();
        }
        changed = true;
      }
    }
    return changed;
  }

  private static Expression buildFactoryAnonymousClass(String body) {
    String publisherType = PUBLISHER_FQN + "<" + NEW_TYPE_FQN + ">";
    return StaticJavaParser.parseExpression(
        "new " + FACTORY_FQN + "() {"
            + "@Override public " + publisherType + " newPublisher(" + PARTITION_FQN + " partition) {"
            + "return " + body + ";"
            + "}"
            + "@Override public void close() {}"
            + "}");
  }
}
