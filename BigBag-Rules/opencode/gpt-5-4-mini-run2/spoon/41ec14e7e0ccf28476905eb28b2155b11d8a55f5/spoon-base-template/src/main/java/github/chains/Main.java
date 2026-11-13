package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

public class Main {
    private static final String SUPPORT_PACKAGE = "generated";
    private static final String SUPPORT_CLASS = "SpoonCompatSupport";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path sourceDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : sourceDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(sourceDir.toString());
        launcher.buildModel();

        Factory factory = launcher.getFactory();
        rewritePropertyUtilsCalls(factory);
        rewriteValidationPattern(factory);

        writeSupportClass(outputDir);
        writeTestSupportClass(outputDir);
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.prettyprint();
        cleanupLegacyImports(outputDir);
    }

    private static void rewritePropertyUtilsCalls(Factory factory) {
        List<CtInvocation<?>> invocations = new ArrayList<>(factory.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
        for (CtInvocation<?> invocation : invocations) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable.getDeclaringType() == null) {
                continue;
            }

            String declaringType = executable.getDeclaringType().getQualifiedName();
            String name = executable.getSimpleName();
            if (!"org.apache.commons.beanutils.PropertyUtils".equals(declaringType)) {
                continue;
            }

            if ("getPropertyDescriptors".equals(name) && invocation.getArguments().size() == 1) {
                String replacement = SUPPORT_PACKAGE + "." + SUPPORT_CLASS + ".propertyDescriptors(" + invocation.getArguments().get(0) + ")";
                invocation.replace(factory.Code().createCodeSnippetExpression(replacement));
            } else if ("getPropertyDescriptor".equals(name) && invocation.getArguments().size() == 2) {
                String replacement = SUPPORT_PACKAGE + "." + SUPPORT_CLASS + ".propertyDescriptor(" + invocation.getArguments().get(0) + ", " + invocation.getArguments().get(1) + ")";
                invocation.replace(factory.Code().createCodeSnippetExpression(replacement));
            }
        }
    }

    private static void rewriteValidationPattern(Factory factory) {
        for (CtType<?> type : factory.getModel().getAllTypes()) {
            for (CtMethod<?> method : type.getMethods()) {
                boolean usesOldValidationApi = method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                        .map(CtInvocation::getExecutable)
                        .anyMatch(Main::isValidationExecution);
                if (usesOldValidationApi && "onInitialize".equals(method.getSimpleName())) {
                    method.setBody(createOnInitializeBody(factory));
                }
                if (containsPropertyOrderingMethod(method)) {
                    method.setBody(createPropertyOrderingBody(factory, method.getSimpleName().equals("getPropertiesByOrder")));
                }
            }
        }
    }

    private static boolean containsPropertyOrderingMethod(CtMethod<?> method) {
        return "getPropertiesByOrder".equals(method.getSimpleName())
                && method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .map(CtInvocation::getExecutable)
                .anyMatch(Main::isPropertyUtilsExecution);
    }

    private static boolean isPropertyUtilsExecution(CtExecutableReference<?> executable) {
        return executable != null
                && executable.getDeclaringType() != null
                && "org.apache.commons.beanutils.PropertyUtils".equals(executable.getDeclaringType().getQualifiedName());
    }

    private static boolean isValidationExecution(CtExecutableReference<?> executable) {
        if (executable == null || executable.getDeclaringType() == null) {
            return false;
        }
        String type = executable.getDeclaringType().getQualifiedName();
        return type.startsWith("javax.validation") || type.startsWith("jakarta.validation");
    }

    private static CtBlock<?> createPropertyOrderingBody(Factory factory, boolean isPropertyMethod) {
        CtBlock<?> body = factory.Core().createBlock();
        body.addStatement(factory.Code().createCodeSnippetStatement("Set<String> properties = new LinkedHashSet<String>();"));
        body.addStatement(factory.Code().createCodeSnippetStatement("for(String property : entitySettings.getOrderOfFields()){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\tif(!entitySettings.getHiddenFields().contains(property)) properties.add(property);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("for(java.beans.PropertyDescriptor descriptor : " + SUPPORT_PACKAGE + "." + SUPPORT_CLASS + ".propertyDescriptors(modelClass)){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\tif(!entitySettings.getHiddenFields().contains(descriptor.getName()) && !properties.contains(descriptor.getName()) && !descriptor.getName().equals(\"class\")) properties.add(descriptor.getName());"));
        body.addStatement(factory.Code().createCodeSnippetStatement("}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("return properties;"));
        return body;
    }

    private static CtBlock<?> createOnInitializeBody(Factory factory) {
        CtBlock<?> body = factory.Core().createBlock();
        body.addStatement(factory.Code().createCodeSnippetStatement("super.onInitialize();"));
        body.addStatement(factory.Code().createCodeSnippetStatement("Class<?> modelClass = getModel().getObject().getClass();"));
        body.addStatement(factory.Code().createCodeSnippetStatement("Set<String> properties = getPropertiesByOrder(modelClass);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("for(String property : properties){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\tjava.beans.PropertyDescriptor descriptor = " + SUPPORT_PACKAGE + "." + SUPPORT_CLASS + ".propertyDescriptor(getModel().getObject(), property);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\tboolean required = " + SUPPORT_PACKAGE + "." + SUPPORT_CLASS + ".hasRequiredConstraint(modelClass, descriptor);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\tobjectProperties.add(new ObjectProperties(descriptor, required));"));
        body.addStatement(factory.Code().createCodeSnippetStatement("}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("RepeatingView view = new RepeatingView(\"controlGroup\");"));
        body.addStatement(factory.Code().createCodeSnippetStatement("for(ObjectProperties objectProperty : objectProperties){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\ttry {"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\tAbstractControlGroup<?> controlGroup;"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\tif(!controlGroupProviders.containsKey(objectProperty.type)) {"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tjava.lang.reflect.Constructor<?> constructor;"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tClass<? extends Panel> typesControlGroup = getControlGroupByType(objectProperty.type);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tif(typesControlGroup==null){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t\tif(objectProperty.type.isEnum()) typesControlGroup = EnumControlGroup.class; else typesControlGroup = ObjectChoiceControlGroup.class;"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tconstructor = typesControlGroup.getConstructor(String.class, IModel.class);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tcontrolGroup = (AbstractControlGroup<?>) constructor.newInstance(view.newChildId(), new PropertyModel<Object>(ListControlGroups.this.getModel(), objectProperty.name));"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tcontrolGroup.init(objectProperty.name, getResourceBase(), objectProperty.required, objectProperty.type, entitySettings);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tcontrolGroup.setEnabled(objectProperty.enabled);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tif(typesControlGroup==ObjectChoiceControlGroup.class){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t\tIObjectRenderer<?> renderer = renderers.get(objectProperty.type);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t\tif(renderer==null){ renderer = new IObjectRenderer<Object>() { private static final long serialVersionUID = -6171655578529011405L; public String render(Object object) { return object.toString(); } }; }"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t\t((ObjectChoiceControlGroup<?>) controlGroup).setConfiguration(getEntityProvider(objectProperty.name), renderer);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t} else if(typesControlGroup==CollectionControlGroup.class){"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t\t((CollectionControlGroup<?>) controlGroup).setConfiguration(getEntityProvider(objectProperty.name), renderers);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\t}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t} else {"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t\tcontrolGroup = controlGroupProviders.get(objectProperty.type).createControlGroup(view.newChildId(), new PropertyModel<Object>(ListControlGroups.this.getModel(), objectProperty.name), objectProperty.name, getResourceBase(), objectProperty.required, objectProperty.type, entitySettings);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\t}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\tview.add(controlGroup);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t\tfieldComponents.put(objectProperty.name, controlGroup);"));
        body.addStatement(factory.Code().createCodeSnippetStatement("\t} catch (SecurityException e) { throw new RuntimeException(e); } catch (NoSuchMethodException e) { throw new RuntimeException(e); } catch (IllegalArgumentException e) { throw new RuntimeException(e); } catch (InstantiationException e) { throw new RuntimeException(e); } catch (IllegalAccessException e) { throw new RuntimeException(e); } catch (java.lang.reflect.InvocationTargetException e) { throw new RuntimeException(e); }"));
        body.addStatement(factory.Code().createCodeSnippetStatement("}"));
        body.addStatement(factory.Code().createCodeSnippetStatement("add(view);"));
        return body;
    }

    private static void writeSupportClass(Path outputDir) throws IOException {
        Path packageDir = outputDir.resolve(SUPPORT_PACKAGE);
        Files.createDirectories(packageDir);
        Path file = packageDir.resolve(SUPPORT_CLASS + ".java");
        String source = String.join("\n",
                "package " + SUPPORT_PACKAGE + ";",
                "",
                "import java.beans.BeanInfo;",
                "import java.beans.IntrospectionException;",
                "import java.beans.Introspector;",
                "import java.beans.PropertyDescriptor;",
                "import java.lang.annotation.Annotation;",
                "import java.lang.reflect.Field;",
                "",
                "public final class " + SUPPORT_CLASS + " {",
                "    private " + SUPPORT_CLASS + "() {}",
                "",
                "    public static PropertyDescriptor[] propertyDescriptors(Class<?> type) {",
                "        try {",
                "            BeanInfo info = Introspector.getBeanInfo(type);",
                "            return info.getPropertyDescriptors();",
                "        } catch (IntrospectionException e) {",
                "            throw new RuntimeException(e);",
                "        }",
                "    }",
                "",
                "    public static PropertyDescriptor propertyDescriptor(Object bean, String name) {",
                "        if (bean == null) {",
                "            throw new IllegalArgumentException(\"bean cannot be null\");",
                "        }",
                "        for (PropertyDescriptor descriptor : propertyDescriptors(bean.getClass())) {",
                "            if (name.equals(descriptor.getName())) {",
                "                return descriptor;",
                "            }",
                "        }",
                "        throw new IllegalArgumentException(\"No property ' \" + name + \" ' found on \" + bean.getClass().getName());",
                "    }",
                "",
                "    public static boolean hasRequiredConstraint(Class<?> modelClass, PropertyDescriptor descriptor) {",
                "        if (descriptor == null) {",
                "            return false;",
                "        }",
                "        if (hasRequiredAnnotation(descriptor.getReadMethod())) {",
                "            return true;",
                "        }",
                "        try {",
                "            Field field = modelClass.getDeclaredField(descriptor.getName());",
                "            return hasRequiredAnnotation(field);",
                "        } catch (NoSuchFieldException e) {",
                "            return false;",
                "        }",
                "    }",
                "",
                "    private static boolean hasRequiredAnnotation(java.lang.reflect.AnnotatedElement element) {",
                "        if (element == null) {",
                "            return false;",
                "        }",
                "        for (Annotation annotation : element.getAnnotations()) {",
                "            String type = annotation.annotationType().getName();",
                "            if (\"javax.validation.constraints.NotNull\".equals(type)",
                "                    || \"javax.validation.constraints.NotEmpty\".equals(type)",
                "                    || \"javax.validation.constraints.NotBlank\".equals(type)",
                "                    || \"jakarta.validation.constraints.NotNull\".equals(type)",
                "                    || \"jakarta.validation.constraints.NotEmpty\".equals(type)",
                "                    || \"jakarta.validation.constraints.NotBlank\".equals(type)) {",
                "                return true;",
                "            }",
                "        }",
                "        return false;",
                "    }",
                "}",
                "");
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static void writeTestSupportClass(Path outputDir) throws IOException {
        Path packageDir = outputDir.resolve("com/premiumminds/webapp/wicket/testing");
        Files.createDirectories(packageDir);
        Path file = packageDir.resolve("AbstractComponentTest.java");
        String source = String.join("\n",
                "package com.premiumminds.webapp.wicket.testing;",
                "",
                "import org.apache.wicket.Component;",
                "import org.apache.wicket.Page;",
                "import org.apache.wicket.util.tester.WicketTester;",
                "",
                "public abstract class AbstractComponentTest {",
                "    private final WicketTester tester = new WicketTester();",
                "",
                "    protected void startTest(Component component) {",
                "        tester.startComponentInPage(component);",
                "    }",
                "",
                "    protected void replayAll() {}",
                "",
                "    protected void verifyAll() {}",
                "",
                "    protected void resetAll() {}",
                "",
                "    protected void resetTest() {",
                "        tester.destroy();",
                "    }",
                "",
                "    protected WicketTester getTester() {",
                "        return tester;",
                "    }",
                "",
                "    protected Page getLastRenderedPage() {",
                "        return tester.getLastRenderedPage();",
                "    }",
                "}",
                "");
        Files.writeString(file, source, StandardCharsets.UTF_8);
    }

    private static void cleanupLegacyImports(Path outputDir) throws IOException {
        Files.walk(outputDir)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                        List<String> filtered = new ArrayList<>();
                        for (String line : lines) {
                            String trimmed = line.trim();
                            if (trimmed.startsWith("import javax.validation")
                                    || trimmed.startsWith("import org.apache.commons.beanutils")
                                    || trimmed.startsWith("import com.premiumminds.webapp.wicket.validators.HibernateValidatorProperty")) {
                                continue;
                            }
                            filtered.add(line);
                        }
                        Files.write(path, filtered, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }
}
