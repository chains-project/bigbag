package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {

  private static final List<Replacement> REPLACEMENTS =
      List.of(
          new Replacement(
              "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter",
              "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter"));

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: Main <source-root>");
    }

    Path root = Paths.get(args[0]);
    if (!Files.isDirectory(root)) {
      throw new IllegalArgumentException("Not a directory: " + root);
    }

    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
    }
  }

  private static void transformFile(Path file) {
    try {
      CompilationUnit cu = StaticJavaParser.parse(file);
      boolean changed = false;
      for (Replacement replacement : REPLACEMENTS) {
        changed |= applyReplacement(cu, replacement);
      }

      if (changed) {
        Files.writeString(file, cu.toString(), StandardCharsets.UTF_8);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to transform " + file, e);
    }
  }

  private static boolean applyReplacement(CompilationUnit cu, Replacement replacement) {
    boolean changed = false;

    for (ImportDeclaration importDeclaration : cu.getImports()) {
      if (replacement.matches(importDeclaration.getNameAsString())) {
        importDeclaration.setName(replacement.newFqcn());
        changed = true;
      }
    }

    for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
      if (replacement.matches(type.getNameWithScope())) {
        type.replace(StaticJavaParser.parseClassOrInterfaceType(replacement.newFqcn()));
        changed = true;
      }
    }

    for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
      if (replacement.matches(classExpr.getType().asString())) {
        classExpr.setType(StaticJavaParser.parseClassOrInterfaceType(replacement.newFqcn()));
        changed = true;
      }
    }

    return changed;
  }

  private static final class Replacement {
    private final String oldFqcn;
    private final String newFqcn;

    private Replacement(String oldFqcn, String newFqcn) {
      this.oldFqcn = oldFqcn;
      this.newFqcn = newFqcn;
    }

    private String oldPackagePrefix() {
      return oldFqcn.substring(0, oldFqcn.lastIndexOf('.'));
    }

    private String oldSimpleName() {
      return oldFqcn.substring(oldFqcn.lastIndexOf('.') + 1);
    }

    private String newFqcn() {
      return newFqcn;
    }

    boolean matches(String candidate) {
      return Objects.equals(candidate, oldFqcn)
          || Objects.equals(candidate, oldPackagePrefix())
          || Objects.equals(candidate, oldSimpleName())
          || candidate != null && candidate.startsWith(oldPackagePrefix() + ".");
    }
  }
}
