package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithType;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class Main {
  private static final String OLD_METADATA = "PublishMetadata";
  private static final String NEW_METADATA = "MessageMetadata";
  private static final String FACTORY_TYPE =
      "com.google.cloud.pubsublite.internal.wire.PartitionPublisherFactory";
    private static final String PARTITION_TYPE = "com.google.cloud.pubsublite.Partition";
    private static final String PUBLISHER_TYPE = "com.google.cloud.pubsublite.internal.Publisher";
    private static final String METADATA_TYPE = "com.google.cloud.pubsublite.MessageMetadata";
    private static final String API_EXCEPTION_TYPE = "com.google.api.gax.rpc.ApiException";
    private static final Pattern SET_CONTEXT = Pattern.compile("\\.setContext\\([^)]*\\)");
    private static final Pattern PUBSUB_CONTEXT_CHAIN =
        Pattern.compile("\\.setContext\\(com\\.google\\.cloud\\.pubsublite\\.internal\\.wire\\.PubsubContext\\.of\\([^)]*\\)\\)");

  public static void main(String[] args) throws IOException {
    if (args.length < 1) {
      throw new IllegalArgumentException("Usage: Main <source-root>");
    }
    Path root = Paths.get(args[0]);
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Not a directory: " + root);
    }

    Files.walk(root)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(Main::transformFile);
  }

  private static void transformFile(Path path) {
    try {
      String original = Files.readString(path, StandardCharsets.UTF_8);
      JavaParser parser = new JavaParser(new ParserConfiguration());
      CompilationUnit cu = parser.parse(original).getResult().orElseThrow();
      CompilationUnit transformed = new ApiTransformer().visit(cu, null);
      String updated = transformed.toString();
      updated = PUBSUB_CONTEXT_CHAIN.matcher(updated).replaceAll("");
      if (!original.equals(updated)) {
        Files.writeString(path, updated, StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to transform " + path, e);
    }
  }

  private static class ApiTransformer extends ModifierVisitor<Void> {
    @Override
    public CompilationUnit visit(CompilationUnit cu, Void arg) {
      cu.getImports()
          .forEach(
              importDecl -> {
                String name = importDecl.getNameAsString();
                if (name.endsWith("." + OLD_METADATA)) {
                  importDecl.setName(
                      name.substring(0, name.length() - OLD_METADATA.length()) + NEW_METADATA);
                }
              });
      super.visit(cu, arg);
      return cu;
    }

    @Override
    public ClassOrInterfaceType visit(ClassOrInterfaceType type, Void arg) {
      if (type.getNameAsString().equals(OLD_METADATA)) {
        type.setName(NEW_METADATA);
      }
      super.visit(type, arg);
      return type;
    }

    @Override
    public VariableDeclarationExpr visit(VariableDeclarationExpr expr, Void arg) {
      VariableDeclarationExpr visited = (VariableDeclarationExpr) super.visit(expr, arg);
      if (isFactoryType(visited.getElementType())) {
        visited.getVariables()
            .forEach(
                variable ->
                    variable
                        .getInitializer()
                        .ifPresent(
                            initializer -> {
                              Expression replacement = transformFactoryExpression(initializer);
                              if (replacement != null) {
                                variable.setInitializer(replacement);
                              }
                            }));
      }
      return visited;
    }

    @Override
    public Visitable visit(MethodCallExpr call, Void arg) {
      MethodCallExpr visited = (MethodCallExpr) super.visit(call, arg);
      if (visited.getNameAsString().equals("setContext") && visited.getArguments().size() == 1) {
        return visited.getScope().orElse(visited);
      }
      if (visited.getNameAsString().equals("setPublisherFactory") && visited.getArguments().size() == 1) {
        Expression replacement = transformFactoryExpression(visited.getArgument(0));
        if (replacement != null) {
          visited.setArgument(0, replacement);
        }
      }
      return visited;
    }

    private Expression transformFactoryExpression(Expression expression) {
      if (!expression.isLambdaExpr()) {
        return null;
      }
      LambdaExpr lambda = expression.asLambdaExpr();
      if (!shouldTransform(lambda)) {
        return null;
      }
      return buildFactoryAnonymousClass(lambda);
    }

    private boolean shouldTransform(LambdaExpr lambda) {
      if (lambda.findAncestor(MethodCallExpr.class)
          .map(call -> call.getNameAsString().equals("setPublisherFactory"))
          .orElse(false)) {
        return true;
      }
      return lambda.findAncestor(VariableDeclarationExpr.class)
          .map(expr -> isFactoryType(expr.getElementType()))
          .orElse(false);
    }

    private boolean isFactoryType(Type type) {
      String rendered = type.asString();
      return rendered.equals("PartitionPublisherFactory") || rendered.equals(FACTORY_TYPE);
    }

    private Expression buildFactoryAnonymousClass(LambdaExpr lambda) {
      Statement bodyStatement = lambda.getBody();
      String methodBody;
      if (bodyStatement.isBlockStmt()) {
        methodBody = bodyStatement.asBlockStmt().toString();
      } else if (bodyStatement.isExpressionStmt()) {
        methodBody = "{ return " + bodyStatement.asExpressionStmt().getExpression() + "; }";
      } else {
        methodBody = "{ return null; }";
      }
      methodBody = SET_CONTEXT.matcher(methodBody).replaceAll("");
      String anonymousSource =
          "new "
              + FACTORY_TYPE
              + "() { @Override public "
              + PUBLISHER_TYPE
              + "<"
              + METADATA_TYPE
              + "> newPublisher("
              + PARTITION_TYPE
              + " partition) throws "
              + API_EXCEPTION_TYPE
              + " "
              + methodBody
              + " @Override public void close() {} }";
      return StaticJavaParser.parseExpression(anonymousSource);
    }
  }
}
