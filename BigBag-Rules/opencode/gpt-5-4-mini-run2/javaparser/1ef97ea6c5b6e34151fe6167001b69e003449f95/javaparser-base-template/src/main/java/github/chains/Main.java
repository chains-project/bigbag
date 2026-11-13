package github.chains;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
  private static final String DATE_TYPE = "Date";
  private static final String TIMESTAMP_TYPE = "Timestamp";

  public static void main(String[] args) throws IOException {
    Path sourceRoot = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
    ParserConfiguration configuration = new ParserConfiguration();
    CombinedTypeSolver typeSolver = new CombinedTypeSolver();
    typeSolver.add(new ReflectionTypeSolver());
    if (Files.isDirectory(sourceRoot)) {
      typeSolver.add(new JavaParserTypeSolver(sourceRoot));
    }
    configuration.setSymbolResolver(new JavaSymbolSolver(typeSolver));
    StaticJavaParser.setConfiguration(configuration);

    List<Path> javaFiles = new ArrayList<>();
    try (var paths = Files.walk(sourceRoot)) {
      paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
          .forEach(javaFiles::add);
    }

    for (Path javaFile : javaFiles) {
      CompilationUnit compilationUnit;
      try {
        compilationUnit = StaticJavaParser.parse(javaFile);
      } catch (Exception e) {
        continue;
      }

      LexicalPreservingPrinter.setup(compilationUnit);
      boolean modified = transform(compilationUnit);
      if (modified) {
        Files.writeString(javaFile, LexicalPreservingPrinter.print(compilationUnit), StandardCharsets.UTF_8);
      }
    }
  }

  private static boolean transform(CompilationUnit compilationUnit) {
    boolean modified = false;

    for (MethodDeclaration methodDeclaration : compilationUnit.findAll(MethodDeclaration.class)) {
      if (isOldBetweenDeclaration(methodDeclaration)) {
        methodDeclaration.getParameters().forEach(parameter -> parameter.setType(TIMESTAMP_TYPE));
        ensureImport(compilationUnit, "java.sql.Timestamp");
        modified = true;
      }
    }

    for (MethodCallExpr methodCallExpr : compilationUnit.findAll(MethodCallExpr.class)) {
      if (!"between".equals(methodCallExpr.getNameAsString()) || methodCallExpr.getArguments().size() != 2) {
        continue;
      }

      for (int i = 0; i < methodCallExpr.getArguments().size(); i++) {
        Expression argument = methodCallExpr.getArgument(i);
        if (isDateLike(argument)) {
          methodCallExpr.setArgument(i, toTimestampExpression(argument));
          ensureImport(compilationUnit, "java.sql.Timestamp");
          modified = true;
        }
      }
    }

    for (ObjectCreationExpr objectCreationExpr : compilationUnit.findAll(ObjectCreationExpr.class)) {
      if (!isTimestampConstruction(objectCreationExpr) || objectCreationExpr.getArguments().size() != 1) {
        continue;
      }

      Expression argument = objectCreationExpr.getArgument(0);
      if (!argument.isMethodCallExpr()) {
        continue;
      }

      MethodCallExpr getTimeCall = argument.asMethodCallExpr();
      if (!"getTime".equals(getTimeCall.getNameAsString()) || getTimeCall.getArguments().size() != 0) {
        continue;
      }

      if (!getTimeCall.getScope().isPresent() || !getTimeCall.getScope().get().isMethodCallExpr()) {
        continue;
      }

      MethodCallExpr innerCall = getTimeCall.getScope().get().asMethodCallExpr();
      if (!"between".equals(innerCall.getNameAsString()) || innerCall.getArguments().size() != 2) {
        continue;
      }

      objectCreationExpr.replace(innerCall.clone());
      modified = true;
    }

    if (modified) {
      cleanupImports(compilationUnit);
    }

    return modified;
  }

  private static boolean isOldBetweenDeclaration(MethodDeclaration methodDeclaration) {
    if (!"between".equals(methodDeclaration.getNameAsString()) || methodDeclaration.getParameters().size() != 2) {
      return false;
    }

    return methodDeclaration.getParameter(0).getType().asString().endsWith(DATE_TYPE)
        && methodDeclaration.getParameter(1).getType().asString().endsWith(DATE_TYPE);
  }

  private static boolean isTimestampConstruction(ObjectCreationExpr objectCreationExpr) {
    return objectCreationExpr.getType().asString().equals(TIMESTAMP_TYPE)
        || objectCreationExpr.getType().asString().equals("java.sql." + TIMESTAMP_TYPE);
  }

  private static boolean isDateLike(Expression expression) {
    try {
      String resolvedType = expression.calculateResolvedType().describe();
      return resolvedType.equals("java.util.Date") || resolvedType.endsWith(".Date");
    } catch (UnsolvedSymbolException | UnsupportedOperationException | IllegalStateException e) {
      return false;
    }
  }

  private static Expression toTimestampExpression(Expression expression) {
    MethodCallExpr getTimeCall = new MethodCallExpr(expression.clone(), "getTime");
    return new ObjectCreationExpr(null, new ClassOrInterfaceType(null, TIMESTAMP_TYPE), NodeList.nodeList(getTimeCall));
  }

  private static void ensureImport(CompilationUnit compilationUnit, String qualifiedName) {
    if (!compilationUnit.getImports().stream().anyMatch(importDecl -> importDecl.getNameAsString().equals(qualifiedName))) {
      compilationUnit.addImport(qualifiedName);
    }
  }

  private static void cleanupImports(CompilationUnit compilationUnit) {
    boolean usesDate = compilationUnit.findAll(com.github.javaparser.ast.type.Type.class).stream()
        .anyMatch(type -> type.asString().equals(DATE_TYPE));
    if (!usesDate) {
      compilationUnit.getImports().removeIf(importDecl -> importDecl.getNameAsString().equals("java.util.Date"));
    }
  }
}
