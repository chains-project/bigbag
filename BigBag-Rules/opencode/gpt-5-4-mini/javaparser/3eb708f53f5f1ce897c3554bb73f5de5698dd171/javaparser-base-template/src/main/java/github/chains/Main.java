package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class Main {

    private static final String OLD_BASE = "com.google.api.services.translate";
    private static final String OLD_MODEL_BASE = "com.google.api.services.translate.model";
    private static final String NEW_BASE = "com.google.api.services.translate.v3";
    private static final String NEW_MODEL_BASE = "com.google.api.services.translate.v3.model";

    private static final Map<String, String> TYPE_RENAMES = new HashMap<>();
    private static final Map<String, String> METHOD_RENAMES = new HashMap<>();

    static {
        TYPE_RENAMES.put("DetectionsResourceItems", "DetectedLanguage");
        TYPE_RENAMES.put("LanguagesResource", "SupportedLanguage");
        TYPE_RENAMES.put("TranslationsResource", "Translation");

        METHOD_RENAMES.put("getLanguage", "getLanguageCode");
        METHOD_RENAMES.put("setLanguage", "setLanguageCode");
        METHOD_RENAMES.put("getName", "getDisplayName");
        METHOD_RENAMES.put("setName", "setDisplayName");
        METHOD_RENAMES.put("getDetectedSourceLanguage", "getDetectedLanguageCode");
        METHOD_RENAMES.put("setDetectedSourceLanguage", "setDetectedLanguageCode");
    }

    public static void main(String[] args) throws Exception {
        Path root = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        if (!Files.exists(root)) {
            throw new IllegalArgumentException("Input source directory does not exist: " + root);
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Main::isJavaSource).forEach(Main::transformFileSafely);
        }
    }

    private static boolean isJavaSource(Path path) {
        String value = path.toString();
        if (!value.endsWith(".java")) {
            return false;
        }
        return !value.contains("/target/") && !value.contains("/build/") && !value.contains("/.git/");
    }

    private static void transformFileSafely(Path file) {
        try {
            transformFile(file);
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + file, e);
        }
    }

    private static void transformFile(Path file) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        CompilationUnit cu = StaticJavaParser.parse(original);
        LexicalPreservingPrinter.setup(cu);

        Set<String> topLevelTypeNames = new HashSet<>();
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!type.isNestedType()) {
                topLevelTypeNames.add(type.getNameAsString());
            }
        }

        boolean changed = false;
        changed |= rewriteImports(cu);
        changed |= rewriteTypes(cu, topLevelTypeNames);
        changed |= rewriteMethodNames(cu);
        changed |= rewriteHttpTranslateRpc(cu);
        changed |= rewriteTranslateDomainMethods(cu, topLevelTypeNames);

        if (changed) {
            Files.writeString(file, LexicalPreservingPrinter.print(cu), StandardCharsets.UTF_8);
        }
    }

    private static boolean rewriteImports(CompilationUnit cu) {
        boolean changed = false;
        List<ImportDeclaration> imports = new ArrayList<>(cu.getImports());
        for (ImportDeclaration imp : imports) {
            String name = imp.getNameAsString();
            String replacement = null;
            if (name.startsWith(OLD_MODEL_BASE)) {
                replacement = NEW_MODEL_BASE + name.substring(OLD_MODEL_BASE.length());
            } else if (name.startsWith(OLD_BASE)) {
                replacement = NEW_BASE + name.substring(OLD_BASE.length());
            }
            if (replacement != null && !replacement.equals(name)) {
                imp.setName(replacement);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTypes(CompilationUnit cu, Set<String> topLevelTypeNames) {
        boolean changed = false;
        for (ClassOrInterfaceType type : cu.findAll(ClassOrInterfaceType.class)) {
            String name = type.getNameAsString();
            if (TYPE_RENAMES.containsKey(name)) {
                if ("Translation".equals(TYPE_RENAMES.get(name)) && topLevelTypeNames.contains("Translation")) {
                    type.replace(StaticJavaParser.parseType(NEW_MODEL_BASE + ".Translation"));
                } else {
                    type.setName(TYPE_RENAMES.get(name));
                }
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteMethodNames(CompilationUnit cu) {
        boolean changed = false;
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            String name = call.getNameAsString();
            if (METHOD_RENAMES.containsKey(name)) {
                call.setName(METHOD_RENAMES.get(name));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean rewriteTranslateDomainMethods(CompilationUnit cu, Set<String> topLevelTypeNames) {
        boolean changed = false;
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            if (method.getNameAsString().equals("fromPb") && method.getParameters().size() == 1) {
                String paramType = method.getParameter(0).getType().asString();
                if (paramType.endsWith("DetectedLanguage")
                    || paramType.endsWith("SupportedLanguage")
                    || paramType.endsWith("Translation")) {
                    method.getBody().ifPresent(body -> {
                        // Body-level accessors were renamed above; nothing else needed.
                    });
                }
            }
        }
        return changed;
    }

    private static boolean rewriteHttpTranslateRpc(CompilationUnit cu) {
        boolean changed = false;
        for (ClassOrInterfaceDeclaration type : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (!type.getNameAsString().equals("HttpTranslateRpc")) {
                continue;
            }
            for (MethodDeclaration method : type.getMethods()) {
                switch (method.getNameAsString()) {
                    case "listSupportedLanguages":
                        changed |= rewriteListSupportedLanguages(method);
                        break;
                    case "detect":
                        changed |= rewriteDetect(method);
                        break;
                    case "translate":
                        changed |= rewriteTranslate(method, type);
                        break;
                    default:
                        break;
                }
            }
        }
        return changed;
    }

    private static boolean rewriteListSupportedLanguages(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return false;
        }
        String block = "{" +
            "    try {\n" +
            "      String parent = getParent();\n" +
            "      java.util.List<com.google.api.services.translate.v3.model.SupportedLanguage> languages =\n" +
            "          translate\n" +
            "              .projects()\n" +
            "              .locations()\n" +
            "              .getSupportedLanguages(parent)\n" +
            "              .setDisplayLanguageCode(\n" +
            "                  firstNonNull(\n" +
            "                      Option.TARGET_LANGUAGE.getString(optionMap), options.getTargetLanguage()))\n" +
            "              .execute()\n" +
            "              .getLanguages();\n" +
            "      return languages != null ? languages : ImmutableList.<com.google.api.services.translate.v3.model.SupportedLanguage>of();\n" +
            "    } catch (IOException ex) {\n" +
            "      throw translate(ex);\n" +
            "    }\n" +
            "  }";
        method.setBody(StaticJavaParser.parseBlock(block));
        return true;
    }

    private static boolean rewriteDetect(MethodDeclaration method) {
        if (!method.getBody().isPresent()) {
            return false;
        }
        String block = "{" +
            "    try {\n" +
            "      java.util.List<java.util.List<com.google.api.services.translate.v3.model.DetectedLanguage>> detections = new java.util.ArrayList<>();\n" +
            "      String parent = getParent();\n" +
            "      for (String text : texts) {\n" +
            "        com.google.api.services.translate.v3.model.DetectLanguageRequest request =\n" +
            "            new com.google.api.services.translate.v3.model.DetectLanguageRequest()\n" +
            "                .setContent(text)\n" +
            "                .setModel(Option.MODEL.getString(optionMap));\n" +
            "        java.util.List<com.google.api.services.translate.v3.model.DetectedLanguage> languages =\n" +
            "            translate.projects().locations().detectLanguage(parent, request).execute().getLanguages();\n" +
            "        detections.add(languages != null ? languages : ImmutableList.<com.google.api.services.translate.v3.model.DetectedLanguage>of());\n" +
            "      }\n" +
            "      return detections;\n" +
            "    } catch (IOException ex) {\n" +
            "      throw translate(ex);\n" +
            "    }\n" +
            "  }";
        method.setBody(StaticJavaParser.parseBlock(block));
        return true;
    }

    private static boolean rewriteTranslate(MethodDeclaration method, ClassOrInterfaceDeclaration type) {
        if (!method.getBody().isPresent()) {
            return false;
        }
        String block = "{" +
            "    try {\n" +
            "      String parent = getParent();\n" +
            "      com.google.api.services.translate.v3.model.TranslateTextRequest request =\n" +
            "          new com.google.api.services.translate.v3.model.TranslateTextRequest()\n" +
            "              .setContents(texts)\n" +
            "              .setTargetLanguageCode(Option.TARGET_LANGUAGE.getString(optionMap))\n" +
            "              .setSourceLanguageCode(Option.SOURCE_LANGUAGE.getString(optionMap))\n" +
            "              .setModel(Option.MODEL.getString(optionMap));\n" +
            "      String format = Option.FORMAT.getString(optionMap);\n" +
            "      if (format != null) {\n" +
            "        request.setMimeType(\"html\".equals(format) ? \"text/html\" : \"text/plain\");\n" +
            "      }\n" +
            "      java.util.List<com.google.api.services.translate.v3.model.Translation> translations =\n" +
            "          translate.projects().locations().translateText(parent, request).execute().getTranslations();\n" +
            "      return Lists.transform(\n" +
            "          translations != null ? translations : ImmutableList.<com.google.api.services.translate.v3.model.Translation>of(),\n" +
            "          new Function<com.google.api.services.translate.v3.model.Translation, com.google.api.services.translate.v3.model.Translation>() {\n" +
            "            @Override\n" +
            "            public com.google.api.services.translate.v3.model.Translation apply(com.google.api.services.translate.v3.model.Translation translationsResource) {\n" +
            "              if (translationsResource.getDetectedLanguageCode() == null) {\n" +
            "                translationsResource.setDetectedLanguageCode(Option.SOURCE_LANGUAGE.getString(optionMap));\n" +
            "              }\n" +
            "              return translationsResource;\n" +
            "            }\n" +
            "          });\n" +
            "    } catch (IOException ex) {\n" +
            "      throw translate(ex);\n" +
            "    }\n" +
            "  }";
        method.setBody(StaticJavaParser.parseBlock(block));
        return true;
    }

    private static String getParentHint(ClassOrInterfaceDeclaration type) {
        return "projects/" + "${projectId}" + "/locations/global";
    }
}
