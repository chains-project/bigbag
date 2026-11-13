package github.chains;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Main {
    private static final String OLD_IMPORT = "org.apache.commons.beanutils.PropertyUtils";
    private static final String DESCRIPTORS_HELPER = "__pmwGeneratedPropertyDescriptors";
    private static final String DESCRIPTOR_HELPER = "__pmwGeneratedPropertyDescriptor";

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single source directory path");
        }

        Path root = Paths.get(args[0]);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Not a directory: " + root);
        }

        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::transformFile);
        }

        transformPom(root);
    }

    private static void transformFile(Path path) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(path);
            if (rewrite(cu)) {
                Files.write(path, cu.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to transform " + path, e);
        }
    }

    private static boolean rewrite(CompilationUnit cu) {
        boolean changed = cu.getImports().removeIf(i -> OLD_IMPORT.equals(i.getNameAsString()));
        Set<ClassOrInterfaceDeclaration> touchedClasses = new LinkedHashSet<>();

        for (MethodCallExpr call : new ArrayList<>(cu.findAll(MethodCallExpr.class))) {
            if (!isPropertyUtilsCall(call)) {
                continue;
            }

            String method = call.getNameAsString();
            if ("getPropertyDescriptors".equals(method) && call.getArguments().size() == 1) {
                call.replace(new MethodCallExpr(null, DESCRIPTORS_HELPER).addArgument(call.getArgument(0).clone()));
                call.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(touchedClasses::add);
                changed = true;
            } else if ("getPropertyDescriptor".equals(method) && call.getArguments().size() == 2) {
                MethodCallExpr replacement = new MethodCallExpr(null, DESCRIPTOR_HELPER)
                        .addArgument(call.getArgument(0).clone())
                        .addArgument(call.getArgument(1).clone());
                call.replace(replacement);
                call.findAncestor(ClassOrInterfaceDeclaration.class).ifPresent(touchedClasses::add);
                changed = true;
            }
        }

        for (ClassOrInterfaceDeclaration clazz : touchedClasses) {
            changed |= ensureHelpers(clazz);
        }

        if (changed) {
            cu.addImport("java.beans.IntrospectionException");
            cu.addImport("java.beans.Introspector");
            cu.addImport("java.beans.PropertyDescriptor");
        }

        return changed;
    }

    private static boolean isPropertyUtilsCall(MethodCallExpr call) {
        return call.getScope()
                .map(scope -> scope.isNameExpr() && "PropertyUtils".equals(scope.asNameExpr().getNameAsString()))
                .orElse(false);
    }

    private static boolean ensureHelpers(ClassOrInterfaceDeclaration clazz) {
        boolean changed = false;
        if (clazz.getMethodsByName(DESCRIPTORS_HELPER).isEmpty()) {
            clazz.addMember(parseMethod("private static PropertyDescriptor[] __pmwGeneratedPropertyDescriptors(Class<?> type) {"
                    + " try {"
                    + " return Introspector.getBeanInfo(type).getPropertyDescriptors();"
                    + " } catch (IntrospectionException e) {"
                    + " throw new RuntimeException(e);"
                    + " }"
                    + " }"));
            changed = true;
        }
        if (clazz.getMethodsByName(DESCRIPTOR_HELPER).isEmpty()) {
            clazz.addMember(parseMethod("private static PropertyDescriptor __pmwGeneratedPropertyDescriptor(Object bean, String property) {"
                    + " if (bean == null || property == null) {"
                    + " return null;"
                    + " }"
                    + " try {"
                    + " for (PropertyDescriptor descriptor : Introspector.getBeanInfo(bean.getClass()).getPropertyDescriptors()) {"
                    + " if (property.equals(descriptor.getName())) {"
                    + " return descriptor;"
                    + " }"
                    + " }"
                    + " return null;"
                    + " } catch (IntrospectionException e) {"
                    + " throw new RuntimeException(e);"
                    + " }"
                    + " }"));
            changed = true;
        }
        return changed;
    }

    private static BodyDeclaration<?> parseMethod(String text) {
        return StaticJavaParser.parseBodyDeclaration(text);
    }

    private static void transformPom(Path root) {
        Path pom = root.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            return;
        }

        try {
            String content = Files.readString(pom);
            String updated = content;
            if (requiresValidationApi(root) && !content.contains("<artifactId>validation-api</artifactId>")) {
                updated = insertDependency(updated,
                        "<dependency>\n"
                                + "            <groupId>javax.validation</groupId>\n"
                                + "            <artifactId>validation-api</artifactId>\n"
                                + "            <version>2.0.1.Final</version>\n"
                                + "        </dependency>");
            }
            if (requiresBeanUtils(root) && !content.contains("<artifactId>commons-beanutils</artifactId>")) {
                updated = insertDependency(updated,
                        "<dependency>\n"
                                + "            <groupId>commons-beanutils</groupId>\n"
                                + "            <artifactId>commons-beanutils</artifactId>\n"
                                + "            <version>1.9.4</version>\n"
                                + "        </dependency>");
            }
            if (!updated.equals(content)) {
                Files.writeString(pom, updated, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to transform pom.xml", e);
        }
    }

    private static boolean requiresValidationApi(Path root) {
        return containsPattern(root, "javax.validation") || containsPattern(root, "jakarta.validation");
    }

    private static boolean requiresBeanUtils(Path root) {
        return containsPattern(root, "org.apache.commons.beanutils") || containsPattern(root, "PropertyUtils");
    }

    private static boolean containsPattern(Path root, String pattern) {
        try (var paths = Files.walk(root)) {
            return paths.filter(path -> path.toString().endsWith(".java")).anyMatch(path -> {
                try {
                    return Files.readString(path).contains(pattern);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String insertDependency(String content, String dependency) {
        int idx = content.indexOf("</dependencies>");
        if (idx < 0) {
            return content;
        }
        return content.substring(0, idx) + "    " + dependency + System.lineSeparator() + content.substring(idx);
    }
}
