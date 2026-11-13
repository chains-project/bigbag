package github.chains;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

public class Main {
  private static final String OLD_TYPE_FQCN =
      "org.apache.struts2.dispatcher.ng.filter.StrutsPrepareAndExecuteFilter";
  private static final String NEW_TYPE_FQCN =
      "org.apache.struts2.dispatcher.filter.StrutsPrepareAndExecuteFilter";

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      throw new IllegalArgumentException("Usage: Main <source-root>");
    }

    Path sourceRoot = Paths.get(args[0]).toAbsolutePath().normalize();
    JavaParser parser = new JavaParser(new ParserConfiguration());

    Files.walkFileTree(
        sourceRoot,
        new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
            if ("target".equals(name) || ".git".equals(name) || ".idea".equals(name)) {
              return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if (!file.toString().endsWith(".java")) {
              return FileVisitResult.CONTINUE;
            }

            String source = Files.readString(file, StandardCharsets.UTF_8);
            Optional<CompilationUnit> parsed = parser.parse(source).getResult();
            if (parsed.isEmpty()) {
              return FileVisitResult.CONTINUE;
            }

            CompilationUnit cu = parsed.get();
            LexicalPreservingPrinter.setup(cu);
            if (!rewriteCompilationUnit(cu)) {
              return FileVisitResult.CONTINUE;
            }

            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  private static boolean rewriteCompilationUnit(CompilationUnit cu) {
    boolean changed = false;

    for (ImportDeclaration importDeclaration : cu.getImports()) {
      if (importDeclaration.getNameAsString().equals(OLD_TYPE_FQCN)) {
        importDeclaration.setName(NEW_TYPE_FQCN);
        changed = true;
      }
    }

    for (Type type : cu.findAll(Type.class)) {
      changed |= replaceQualifiedType(type);
    }

    for (ClassExpr classExpr : cu.findAll(ClassExpr.class)) {
      changed |= replaceQualifiedType(classExpr.getType());
    }

    return changed;
  }

  private static boolean replaceQualifiedType(Type type) {
    if (!type.asString().equals(OLD_TYPE_FQCN)) {
      return false;
    }

    Node replacement = StaticJavaParser.parseClassOrInterfaceType(NEW_TYPE_FQCN);
    type.replace(replacement);
    return true;
  }
}
