package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

public class Main {

  private static final String OBSOLETE_RETURN_TYPE = "jakarta.servlet.http.HttpSessionContext";
  private static final String HTTP_SESSION_TYPE = "jakarta.servlet.http.HttpSession";
  private static final Set<String> OBSOLETE_HTTP_SESSION_METHODS = Set.of(
      "getSessionContext",
      "getValue",
      "getValueNames",
      "putValue",
      "removeValue");

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: Main <input-source-dir> [output-source-dir]");
      System.exit(1);
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = args.length > 1 ? Paths.get(args[1]) : inputDir.resolveSibling(inputDir.getFileName() + "-transformed");

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.getEnvironment().setComplianceLevel(17);
    launcher.getEnvironment().setSourceOutputDirectory(outputDir.toFile());
    launcher.addInputResource(inputDir.toString());
    launcher.buildModel();

    List<CtMethod<?>> methods = launcher.getModel().getElements(
        element -> element instanceof CtMethod<?> && shouldDelete((CtMethod<?>) element));
    for (CtMethod<?> method : methods) {
      method.delete();
    }

    launcher.prettyprint();
  }

  private static boolean shouldDelete(CtMethod<?> method) {
    if (!OBSOLETE_HTTP_SESSION_METHODS.contains(method.getSimpleName())) {
      return false;
    }

    if (!matchesRemovedSignature(method)) {
      return false;
    }

    CtType<?> declaringType = method.getDeclaringType();
    return declaringType != null && isHttpSessionImplementation(declaringType);
  }

  private static boolean matchesRemovedSignature(CtMethod<?> method) {
    String name = method.getSimpleName();
    if ("getSessionContext".equals(name)) {
      CtTypeReference<?> returnType = method.getType();
      return returnType != null && OBSOLETE_RETURN_TYPE.equals(returnType.getQualifiedName())
          && method.getParameters().isEmpty();
    }
    if ("getValue".equals(name) || "putValue".equals(name) || "removeValue".equals(name)) {
      return method.getParameters().size() == 1;
    }
    if ("getValueNames".equals(name)) {
      return method.getParameters().isEmpty();
    }
    return false;
  }

  private static boolean isHttpSessionImplementation(CtType<?> type) {
    if (type.getSuperclass() != null && HTTP_SESSION_TYPE.equals(type.getSuperclass().getQualifiedName())) {
      return true;
    }
    for (CtTypeReference<?> iface : type.getSuperInterfaces()) {
      if (HTTP_SESSION_TYPE.equals(iface.getQualifiedName())) {
        return true;
      }
    }
    return false;
  }
}
