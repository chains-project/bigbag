package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class Main {

  private static final String OLD_SERVICE_PREFIX = "com.google.api.services.translate";
  private static final String NEW_SERVICE_PREFIX = "com.google.api.services.translate.v3";
  private static final String OLD_MODEL_PREFIX = OLD_SERVICE_PREFIX + ".model";
  private static final String NEW_MODEL_PREFIX = NEW_SERVICE_PREFIX + ".model";

  private static final Map<String, String> IMPORT_REWRITES = new HashMap<>();

  static {
    IMPORT_REWRITES.put("com.google.api.services.translate.Translate", NEW_SERVICE_PREFIX + ".Translate");
    IMPORT_REWRITES.put(
        "com.google.api.services.translate.model.DetectionsResourceItems",
        NEW_MODEL_PREFIX + ".DetectedLanguage");
    IMPORT_REWRITES.put(
        "com.google.api.services.translate.model.LanguagesResource",
        NEW_MODEL_PREFIX + ".SupportedLanguage");
  }

  public static void main(String[] args) throws IOException {
    Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
    StaticJavaParser.setConfiguration(
        new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_11));

    try (Stream<Path> paths = Files.walk(root)) {
      paths.filter(
              path ->
                  Files.isRegularFile(path)
                      && path.toString().endsWith(".java")
                      && !path.toString().contains("/target/"))
          .forEach(
              path -> {
                try {
                  transformFile(path);
                } catch (Exception e) {
                  throw new RuntimeException("Failed to transform " + path, e);
                }
              });
    }
  }

  private static void transformFile(Path path) throws IOException {
    CompilationUnit cu = StaticJavaParser.parse(path);
    LexicalPreservingPrinter.setup(cu);

    boolean changed = false;
    changed |= rewriteImports(cu);
    changed |= rewriteTypes(cu);
    changed |= rewriteMethodNames(cu);
    changed |= rewriteHttpTranslateRpc(cu);

    if (changed) {
      Files.writeString(path, LexicalPreservingPrinter.print(cu));
    }
  }

  private static boolean rewriteImports(CompilationUnit cu) {
    boolean changed = false;
    for (ImportDeclaration importDeclaration : cu.getImports()) {
      String name = importDeclaration.getNameAsString();
      String replacement = IMPORT_REWRITES.get(name);
      if (replacement != null) {
        importDeclaration.setName(replacement);
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rewriteTypes(CompilationUnit cu) {
    boolean changed = false;
    for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
      String name = type.getNameAsString();
      if ("LanguagesResource".equals(name)) {
        type.setName("SupportedLanguage");
        changed = true;
      } else if ("DetectionsResourceItems".equals(name)) {
        type.setName("DetectedLanguage");
        changed = true;
      } else if ("TranslationsResource".equals(name)) {
        type.replace(StaticJavaParser.parseClassOrInterfaceType(NEW_MODEL_PREFIX + ".Translation"));
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rewriteMethodNames(CompilationUnit cu) {
    boolean translateModelSeen = hasModelReference(cu, "LanguagesResource")
        || hasModelReference(cu, "SupportedLanguage")
        || hasModelReference(cu, "DetectionsResourceItems")
        || hasModelReference(cu, "DetectedLanguage")
        || hasTranslationModelReference(cu);
    if (!translateModelSeen) {
      return false;
    }

    boolean changed = false;
    for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
      String name = call.getNameAsString();
      if ("setLanguage".equals(name)) {
        call.setName("setLanguageCode");
        changed = true;
      } else if ("getLanguage".equals(name)) {
        call.setName("getLanguageCode");
        changed = true;
        } else if ("setName".equals(name) && referencesLanguageModel(cu)) {
          call.setName("setDisplayName");
          changed = true;
      } else if ("getName".equals(name) && referencesLanguageModel(cu)) {
          call.setName("getDisplayName");
          changed = true;
      } else if ("setDetectedSourceLanguage".equals(name)) {
        call.setName("setDetectedLanguageCode");
        changed = true;
      } else if ("getDetectedSourceLanguage".equals(name)) {
        call.setName("getDetectedLanguageCode");
        changed = true;
      }
    }
    return changed;
  }

  private static boolean rewriteHttpTranslateRpc(CompilationUnit cu) {
    Optional<ClassOrInterfaceDeclaration> httpTranslateRpc = cu.getClassByName("HttpTranslateRpc");
    if (httpTranslateRpc.isEmpty()) {
      return false;
    }

    boolean changed = false;
    for (MethodDeclaration method : httpTranslateRpc.get().getMethods()) {
      switch (method.getNameAsString()) {
        case "detect":
          method.setBody(
              StaticJavaParser.parseBlock(
                  "{" +
                      "  try {" +
                      "    String parent = \"projects/\" + options.getProjectId();" +
                      "    java.util.List<java.util.List<com.google.api.services.translate.v3.model.DetectedLanguage>> detections = new java.util.ArrayList<>();" +
                      "    for (String text : texts) {" +
                      "      com.google.api.services.translate.v3.model.DetectLanguageRequest request =" +
                      "          new com.google.api.services.translate.v3.model.DetectLanguageRequest()" +
                      "              .setContent(text)" +
                      "              .setModel(Option.MODEL.getString(optionMap))" +
                      "              .setMimeType(Option.FORMAT.getString(optionMap));" +
                      "      com.google.api.services.translate.v3.model.DetectLanguageResponse response =" +
                      "          translate.projects().detectLanguage(parent, request).setKey(options.getApiKey()).execute();" +
                      "      detections.add(" +
                      "          response.getLanguages() != null" +
                      "              ? response.getLanguages()" +
                      "              : com.google.common.collect.ImmutableList.<com.google.api.services.translate.v3.model.DetectedLanguage>of());" +
                      "    }" +
                      "    return detections;" +
                      "  } catch (IOException ex) {" +
                      "    throw translate(ex);" +
                      "  }" +
                      "}"));
          changed = true;
          break;
        case "listSupportedLanguages":
          method.setBody(
              StaticJavaParser.parseBlock(
                  "{" +
                      "  try {" +
                      "    String parent = \"projects/\" + options.getProjectId();" +
                      "    com.google.api.services.translate.v3.model.SupportedLanguages response =" +
                      "        translate.projects()" +
                      "            .getSupportedLanguages(parent)" +
                      "            .setDisplayLanguageCode(" +
                      "                firstNonNull(Option.TARGET_LANGUAGE.getString(optionMap), options.getTargetLanguage()))" +
                      "            .setModel(Option.MODEL.getString(optionMap))" +
                      "            .setKey(options.getApiKey())" +
                      "            .execute();" +
                      "    java.util.List<com.google.api.services.translate.v3.model.SupportedLanguage> languages = response.getLanguages();" +
                      "    return languages != null ? languages : com.google.common.collect.ImmutableList.<com.google.api.services.translate.v3.model.SupportedLanguage>of();" +
                      "  } catch (IOException ex) {" +
                      "    throw translate(ex);" +
                      "  }" +
                      "}"));
          changed = true;
          break;
        case "translate":
          method.setBody(
              StaticJavaParser.parseBlock(
                  "{" +
                      "  try {" +
                      "    String parent = \"projects/\" + options.getProjectId();" +
                      "    String targetLanguage = firstNonNull(Option.TARGET_LANGUAGE.getString(optionMap), options.getTargetLanguage());" +
                      "    final String sourceLanguage = Option.SOURCE_LANGUAGE.getString(optionMap);" +
                      "    com.google.api.services.translate.v3.model.TranslateTextRequest request =" +
                      "        new com.google.api.services.translate.v3.model.TranslateTextRequest()" +
                      "            .setContents(texts)" +
                      "            .setTargetLanguageCode(targetLanguage)" +
                      "            .setSourceLanguageCode(sourceLanguage)" +
                      "            .setModel(Option.MODEL.getString(optionMap))" +
                      "            .setMimeType(Option.FORMAT.getString(optionMap));" +
                      "    java.util.List<com.google.api.services.translate.v3.model.Translation> translations =" +
                      "        translate.projects().translateText(parent, request).setKey(options.getApiKey()).execute().getTranslations();" +
                      "    return com.google.common.collect.Lists.transform(" +
                      "        translations != null" +
                      "            ? translations" +
                      "            : com.google.common.collect.ImmutableList.<com.google.api.services.translate.v3.model.Translation>of()," +
                      "        new com.google.common.base.Function<com.google.api.services.translate.v3.model.Translation, com.google.api.services.translate.v3.model.Translation>() {" +
                      "          @Override" +
                      "          public com.google.api.services.translate.v3.model.Translation apply(" +
                      "              com.google.api.services.translate.v3.model.Translation translationResource) {" +
                      "            if (translationResource.getDetectedLanguageCode() == null) {" +
                      "              translationResource.setDetectedLanguageCode(sourceLanguage);" +
                      "            }" +
                      "            return translationResource;" +
                      "          }" +
                      "        });" +
                      "  } catch (IOException ex) {" +
                      "    throw translate(ex);" +
                      "  }" +
                      "}"));
          changed = true;
          break;
        default:
          break;
      }
    }
    return changed;
  }

  private static boolean hasModelReference(CompilationUnit cu, String simpleName) {
    return cu.getImports().stream().anyMatch(i -> i.getNameAsString().endsWith(simpleName))
        || cu.findAll(ClassOrInterfaceType.class).stream()
            .anyMatch(t -> simpleName.equals(t.getNameAsString()) || t.asString().endsWith(simpleName));
  }

  private static boolean hasTranslationModelReference(CompilationUnit cu) {
    return cu.getImports().stream()
            .anyMatch(
                i ->
                    i.getNameAsString().equals(OLD_MODEL_PREFIX + ".TranslationsResource")
                        || i.getNameAsString().equals(NEW_MODEL_PREFIX + ".Translation"))
        || cu.findAll(ClassOrInterfaceType.class).stream()
            .anyMatch(
                t ->
                    "TranslationsResource".equals(t.getNameAsString())
                        || t.asString().equals(NEW_MODEL_PREFIX + ".Translation"));
  }

  private static boolean hasLanguageModelReference(CompilationUnit cu) {
    return hasModelReference(cu, "LanguagesResource") || hasModelReference(cu, "SupportedLanguage");
  }

  private static boolean referencesLanguageModel(CompilationUnit cu) {
    return hasLanguageModelReference(cu);
  }
}
