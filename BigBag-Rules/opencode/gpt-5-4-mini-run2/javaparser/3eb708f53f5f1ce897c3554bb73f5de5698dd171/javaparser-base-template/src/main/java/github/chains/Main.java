package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
  public static void main(String[] args) throws IOException {
    Path root = Paths.get(args.length > 0 ? args[0] : ".");
    Files.walk(root)
        .filter(p -> p.toString().endsWith(".java"))
        .forEach(
            p -> {
              try {
                rewrite(p);
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
  }

  private static void rewrite(Path path) throws IOException {
    CompilationUnit cu = StaticJavaParser.parse(path);
    LexicalPreservingPrinter.setup(cu);
    boolean changed = false;

    changed |= replaceImport(cu, "com.google.api.services.translate.Translate", "com.google.api.services.translate.v3.Translate");
    changed |= replaceImport(cu, "com.google.api.services.translate.model.DetectionsResourceItems", "com.google.api.services.translate.v3.model.DetectedLanguage");
    changed |= replaceImport(cu, "com.google.api.services.translate.model.LanguagesResource", "com.google.api.services.translate.v3.model.SupportedLanguage");
    changed |= replaceImport(cu, "com.google.api.services.translate.model.TranslationsResource", "com.google.api.services.translate.v3.model.Translation");

    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      changed |= rename(call, "getDetectedSourceLanguage", "getDetectedLanguageCode");
      changed |= rename(call, "setDetectedSourceLanguage", "setDetectedLanguageCode");
      changed |= rename(call, "setTarget", "setDisplayLanguageCode");
      changed |= rename(call, "setSource", "setSourceLanguageCode");

      if (isCall(call, "languages", "list")) {
        call.setScope(StaticJavaParser.parseExpression("translate.projects()"));
        call.setName("getSupportedLanguages");
        call.setArguments(new NodeList<>(genericParentExpr()));
        changed = true;
      }

      if (isCall(call, "translations", "list") && call.getArguments().size() >= 2) {
        Expression texts = call.getArgument(0);
        Expression target = call.getArgument(1);
        call.setScope(StaticJavaParser.parseExpression("translate.projects()"));
        call.setName("translateText");
        call.setArguments(
            new NodeList<>(
                genericParentExpr(),
                StaticJavaParser.parseExpression(
                    "new com.google.api.services.translate.v3.model.TranslateTextRequest()"
                        + ".setContents(" + texts + ")"
                        + ".setTargetLanguageCode(" + target + ")")));
        changed = true;
      }
    }

    if (changed) {
      Files.writeString(path, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
    }
  }

  private static boolean replaceImport(CompilationUnit cu, String oldName, String newName) {
    boolean changed = false;
    for (ImportDeclaration imp : cu.getImports()) {
      if (imp.getNameAsString().equals(oldName)) {
        imp.setName(newName);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rename(MethodCallExpr call, String oldName, String newName) {
    if (call.getNameAsString().equals(oldName)) {
      call.setName(newName);
      return true;
    }
    return false;
  }

  private static boolean isCall(MethodCallExpr call, String scopeName, String methodName) {
    return methodName.equals(call.getNameAsString())
        && call.getScope().isPresent()
        && call.getScope().get() instanceof MethodCallExpr
        && scopeName.equals(((MethodCallExpr) call.getScope().get()).getNameAsString());
  }

  private static Expression genericParentExpr() {
    return StaticJavaParser.parseExpression(
        "String.format(\"projects/%s/locations/global\", System.getProperty(\"GOOGLE_CLOUD_PROJECT\"))");
  }
}
