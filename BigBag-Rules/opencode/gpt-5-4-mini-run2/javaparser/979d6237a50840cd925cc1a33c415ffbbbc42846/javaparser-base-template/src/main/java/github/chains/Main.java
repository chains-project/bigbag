package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class Main {

  private static final String OLD_FQCN = "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
  private static final String NEW_FQCN = "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: Main <source-directory>");
    }

    Path root = Paths.get(args[0]);
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Source directory does not exist: " + root);
    }

    Files.walk(root)
        .filter(path -> path.toString().endsWith(".java"))
        .forEach(Main::transformFile);
  }

  private static void transformFile(Path path) {
    try {
      CompilationUnit cu = StaticJavaParser.parse(path);
      LexicalPreservingPrinter.setup(cu);

      boolean changed = false;

      for (ImportDeclaration importDeclaration : cu.findAll(ImportDeclaration.class)) {
        if (!importDeclaration.isAsterisk()
            && OLD_FQCN.equals(importDeclaration.getNameAsString())) {
          importDeclaration.setName(NEW_FQCN);
          changed = true;
        }
      }

      for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
        if (OLD_FQCN.equals(fullyQualifiedTypeName(type))) {
          type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_FQCN));
          changed = true;
        }
      }

      for (FieldAccessExpr fieldAccessExpr : cu.findAll(FieldAccessExpr.class)) {
        if (OLD_FQCN.equals(qualifiedName(fieldAccessExpr))) {
          fieldAccessExpr.replace(StaticJavaParser.parseExpression(NEW_FQCN));
          changed = true;
        }
      }

      for (NameExpr nameExpr : cu.findAll(NameExpr.class)) {
        if (OLD_FQCN.equals(nameExpr.getNameAsString())) {
          nameExpr.replace(StaticJavaParser.parseExpression(NEW_FQCN));
          changed = true;
        }
      }

      if (changed) {
        Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
      }
    } catch (Exception ignored) {
      // Skip files that fail to parse; only syntactically valid Java sources are rewritten.
    }
  }

  private static String fullyQualifiedTypeName(ClassOrInterfaceType type) {
    Deque<String> parts = new ArrayDeque<>();
    ClassOrInterfaceType current = type;

    while (current != null) {
      parts.addFirst(current.getNameAsString());
      current = current.getScope().orElse(null);
    }

    return String.join(".", parts);
  }

  private static String qualifiedName(Expression expression) {
    if (expression instanceof NameExpr) {
      return ((NameExpr) expression).getNameAsString();
    }
    if (expression instanceof FieldAccessExpr) {
      FieldAccessExpr fieldAccessExpr = (FieldAccessExpr) expression;
      String scope = qualifiedName(fieldAccessExpr.getScope());
      return scope == null ? null : scope + "." + fieldAccessExpr.getNameAsString();
    }
    return null;
  }
}
