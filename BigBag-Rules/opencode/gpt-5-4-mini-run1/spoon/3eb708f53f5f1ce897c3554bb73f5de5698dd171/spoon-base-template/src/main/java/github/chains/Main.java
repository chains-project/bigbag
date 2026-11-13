package github.chains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
  private static final String OLD_SERVICE = "com.google.api.services.translate.Translate";
  private static final String NEW_SERVICE = "com.google.api.services.translate.v3.Translate";
  private static final String OLD_MODEL = "com.google.api.services.translate.model.";
  private static final String NEW_MODEL = "com.google.api.services.translate.v3.model.";

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Expected input source directory");
    }

    String inputDir = args[0];
    String outputDir = args.length > 1 ? args[1] : inputDir;

    Launcher launcher = new Launcher();
    launcher.addInputResource(inputDir);
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.setSourceOutputDirectory(outputDir);
    launcher.buildModel();

    List<CtInvocation<?>> invocations =
        new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
    for (CtInvocation<?> invocation : invocations) {
      rewriteInvocation(invocation);
    }

    List<CtTypeReference<?>> references =
        new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class)));
    for (CtTypeReference<?> reference : references) {
      rewriteTypeReference(reference);
    }

    launcher.prettyprint();
  }

  private static void rewriteTypeReference(CtTypeReference<?> reference) {
    String qualifiedName = reference.getQualifiedName();
    if (OLD_SERVICE.equals(qualifiedName)) {
      applyQualifiedName(reference, NEW_SERVICE);
      return;
    }
    if (!qualifiedName.startsWith(OLD_MODEL)) {
      return;
    }

    String mapped = switch (qualifiedName.substring(OLD_MODEL.length())) {
      case "DetectionsResourceItems" -> NEW_MODEL + "DetectedLanguage";
      case "LanguagesResource" -> NEW_MODEL + "SupportedLanguage";
      case "TranslationsResource" -> NEW_MODEL + "Translation";
      default -> null;
    };
    if (mapped != null) {
      applyQualifiedName(reference, mapped);
    }
  }

  private static void applyQualifiedName(CtTypeReference<?> reference, String qualifiedName) {
    int lastDot = qualifiedName.lastIndexOf('.');
    String packageName = lastDot >= 0 ? qualifiedName.substring(0, lastDot) : "";
    String simpleName = lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    reference.setSimpleName(simpleName);
    CtPackageReference packageReference = reference.getFactory().Core().createPackageReference();
    packageReference.setSimpleName(packageName);
    reference.setPackage(packageReference);
  }

  private static void rewriteInvocation(CtInvocation<?> invocation) {
    if (rewriteTranslateChain(invocation)) {
      return;
    }
    rewriteAccessor(invocation);
  }

  private static void rewriteAccessor(CtInvocation<?> invocation) {
    String name = invocation.getExecutable().getSimpleName();
    String typeName = declaringTypeName(invocation);

    if (isLanguageType(typeName) && "getLanguage".equals(name)) {
      invocation.getExecutable().setSimpleName("getLanguageCode");
    } else if (isLanguageType(typeName) && "setLanguage".equals(name)) {
      invocation.getExecutable().setSimpleName("setLanguageCode");
    } else if (isSupportedLanguageType(typeName) && "getName".equals(name)) {
      invocation.getExecutable().setSimpleName("getDisplayName");
    } else if (isSupportedLanguageType(typeName) && "setName".equals(name)) {
      invocation.getExecutable().setSimpleName("setDisplayName");
    } else if (isTranslationType(typeName) && "getDetectedSourceLanguage".equals(name)) {
      invocation.getExecutable().setSimpleName("getDetectedLanguageCode");
    } else if (isTranslationType(typeName) && "setDetectedSourceLanguage".equals(name)) {
      invocation.getExecutable().setSimpleName("setDetectedLanguageCode");
    }
  }

  private static boolean rewriteTranslateChain(CtInvocation<?> terminalInvocation) {
    String terminalName = terminalInvocation.getExecutable().getSimpleName();
    if (!"getTranslations".equals(terminalName)
        && !"getLanguages".equals(terminalName)
        && !"getDetections".equals(terminalName)) {
      return false;
    }

    List<CtInvocation<?>> chain = collectChain(terminalInvocation);
    if (chain.isEmpty()) {
      return false;
    }

    String rootMethod = chain.get(0).getExecutable().getSimpleName();
    if ("translations".equals(rootMethod) && "getTranslations".equals(terminalName)) {
      replaceTranslationChain(terminalInvocation, chain);
      return true;
    }
    if ("languages".equals(rootMethod) && "getLanguages".equals(terminalName)) {
      replaceLanguagesChain(terminalInvocation, chain);
      return true;
    }
    if ("detections".equals(rootMethod) && "getDetections".equals(terminalName)) {
      replaceDetectionsChain(terminalInvocation, chain);
      return true;
    }
    return false;
  }

  private static void replaceTranslationChain(
      CtInvocation<?> terminalInvocation, List<CtInvocation<?>> chain) {
    CtInvocation<?> listInvocation = findInvocation(chain, "list");
    if (listInvocation == null || listInvocation.getArguments().size() < 2) {
      return;
    }

    CtExpression<?> rootTarget = chain.get(0).getTarget();
    String root = rootTarget == null ? "" : rootTarget.toString();
    String contents = listInvocation.getArguments().get(0).toString();
    String targetLanguage = listInvocation.getArguments().get(1).toString();

    StringBuilder replacement = new StringBuilder();
    replacement.append(root)
        .append(".projects().translateText(\"\", new ")
        .append(NEW_MODEL)
        .append("TranslateTextRequest().setContents(")
        .append(contents)
        .append(")")
        .append(".setTargetLanguageCode(")
        .append(targetLanguage)
        .append(")");

    appendRequestSetters(replacement, chain, "setSource", "setModel", "setFormat");
    replacement.append(")");
    appendCommonSetters(replacement, chain, "setSource", "setModel", "setFormat", "list");
    replacement.append(".execute().getTranslations()");
    terminalInvocation.replace(
        terminalInvocation.getFactory().Code().createCodeSnippetExpression(replacement.toString()));
  }

  private static void replaceLanguagesChain(
      CtInvocation<?> terminalInvocation, List<CtInvocation<?>> chain) {
    CtInvocation<?> listInvocation = findInvocation(chain, "list");
    CtExpression<?> rootTarget = chain.get(0).getTarget();
    String root = rootTarget == null ? "" : rootTarget.toString();
    StringBuilder replacement = new StringBuilder();
    replacement.append(root).append(".projects().getSupportedLanguages(\"\")");

    CtInvocation<?> setTarget = findInvocation(chain, "setTarget");
    if (setTarget != null && !setTarget.getArguments().isEmpty()) {
      replacement.append(".setDisplayLanguageCode(").append(setTarget.getArguments().get(0)).append(")");
    }

    CtInvocation<?> setModel = findInvocation(chain, "setModel");
    if (setModel != null && !setModel.getArguments().isEmpty()) {
      replacement.append(".setModel(").append(setModel.getArguments().get(0)).append(")");
    }

    appendCommonSetters(replacement, chain, "list", "setTarget", "setModel");
    replacement.append(".execute().getLanguages()");
    terminalInvocation.replace(
        terminalInvocation.getFactory().Code().createCodeSnippetExpression(replacement.toString()));
  }

  private static void replaceDetectionsChain(
      CtInvocation<?> terminalInvocation, List<CtInvocation<?>> chain) {
    CtInvocation<?> listInvocation = findInvocation(chain, "list");
    if (listInvocation == null || listInvocation.getArguments().isEmpty()) {
      return;
    }

    CtExpression<?> rootTarget = chain.get(0).getTarget();
    String root = rootTarget == null ? "" : rootTarget.toString();
    String texts = listInvocation.getArguments().get(0).toString();

    StringBuilder requestSetters = new StringBuilder();
    appendCommonRequestSetters(requestSetters, chain, "list");

    StringBuilder replacement = new StringBuilder();
    replacement.append(texts)
        .append(".stream().map(text -> ")
        .append(root)
        .append(".projects().detectLanguage(\"\", new ")
        .append(NEW_MODEL)
        .append("DetectLanguageRequest().setContent(text))");
    replacement.append(requestSetters);
    replacement.append(".execute().getLanguages()).collect(java.util.stream.Collectors.toList())");

    terminalInvocation.replace(
        terminalInvocation.getFactory().Code().createCodeSnippetExpression(replacement.toString()));
  }

  private static void appendRequestSetters(
      StringBuilder replacement, List<CtInvocation<?>> chain, String... ignoredNames) {
    List<String> ignored = List.of(ignoredNames);
    for (CtInvocation<?> invocation : chain) {
      String name = invocation.getExecutable().getSimpleName();
      if (ignored.contains(name) || "execute".equals(name) || name.startsWith("get")) {
        continue;
      }
      if (isCommonRequestSetter(name)) {
        replacement.append('.').append(name).append('(');
        if (!invocation.getArguments().isEmpty()) {
          replacement.append(invocation.getArguments().get(0));
        }
        replacement.append(')');
      } else if ("setSource".equals(name)) {
        replacement.append(".setSourceLanguageCode(")
            .append(firstArgOrEmpty(invocation))
            .append(")");
      } else if ("setFormat".equals(name)) {
        replacement.append(".setMimeType(")
            .append(firstArgOrEmpty(invocation))
            .append(")");
      } else if ("setModel".equals(name)) {
        replacement.append(".setModel(")
            .append(firstArgOrEmpty(invocation))
            .append(")");
      }
    }
  }

  private static void appendCommonSetters(
      StringBuilder replacement, List<CtInvocation<?>> chain, String... ignoredNames) {
    List<String> ignored = List.of(ignoredNames);
    for (CtInvocation<?> invocation : chain) {
      String name = invocation.getExecutable().getSimpleName();
      if (ignored.contains(name) || "execute".equals(name) || name.startsWith("get")) {
        continue;
      }
      if (isCommonRequestSetter(name)) {
        replacement.append('.').append(name).append('(');
        if (!invocation.getArguments().isEmpty()) {
          replacement.append(invocation.getArguments().get(0));
        }
        replacement.append(')');
      }
    }
  }

  private static void appendCommonRequestSetters(
      StringBuilder replacement, List<CtInvocation<?>> chain, String... ignoredNames) {
    List<String> ignored = List.of(ignoredNames);
    for (CtInvocation<?> invocation : chain) {
      String name = invocation.getExecutable().getSimpleName();
      if (ignored.contains(name) || "execute".equals(name) || name.startsWith("get")) {
        continue;
      }
      if (isCommonRequestSetter(name)) {
        replacement.append('.').append(name).append('(');
        if (!invocation.getArguments().isEmpty()) {
          replacement.append(invocation.getArguments().get(0));
        }
        replacement.append(')');
      }
    }
  }

  private static boolean isCommonRequestSetter(String name) {
    return switch (name) {
      case "set",
          "setAccessToken",
          "setAlt",
          "setCallback",
          "setFields",
          "setKey",
          "setOauthToken",
          "setPrettyPrint",
          "setQuotaUser",
          "setUploadProtocol",
          "setUploadType",
          "Xgafv" -> true;
      default -> false;
    };
  }

  private static List<CtInvocation<?>> collectChain(CtInvocation<?> terminalInvocation) {
    List<CtInvocation<?>> chain = new ArrayList<>();
    CtInvocation<?> current = terminalInvocation;
    while (current != null) {
      chain.add(current);
      CtExpression<?> target = current.getTarget();
      if (target instanceof CtInvocation<?>) {
        current = (CtInvocation<?>) target;
      } else {
        break;
      }
    }
    Collections.reverse(chain);
    return chain;
  }

  private static CtInvocation<?> findInvocation(List<CtInvocation<?>> chain, String methodName) {
    for (CtInvocation<?> invocation : chain) {
      if (methodName.equals(invocation.getExecutable().getSimpleName())) {
        return invocation;
      }
    }
    return null;
  }

  private static String firstArgOrEmpty(CtInvocation<?> invocation) {
    if (invocation.getArguments().isEmpty()) {
      return "";
    }
    return invocation.getArguments().get(0).toString();
  }

  private static String declaringTypeName(CtInvocation<?> invocation) {
    if (invocation.getExecutable().getDeclaringType() != null) {
      return invocation.getExecutable().getDeclaringType().getQualifiedName();
    }
    CtExpression<?> target = invocation.getTarget();
    if (target != null && target.getType() != null) {
      return target.getType().getQualifiedName();
    }
    return "";
  }

  private static boolean isLanguageType(String typeName) {
    return typeName.endsWith("LanguagesResource") || typeName.endsWith("DetectedLanguage");
  }

  private static boolean isSupportedLanguageType(String typeName) {
    return typeName.endsWith("LanguagesResource") || typeName.endsWith("SupportedLanguage");
  }

  private static boolean isTranslationType(String typeName) {
    return typeName.endsWith("TranslationsResource") || typeName.endsWith("Translation");
  }
}
