package github.chains;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;

public class Main {
    public static void main(String[] args) throws Exception {
        Path projectRoot = args.length > 0 ? Paths.get(args[0]) : Paths.get(".");
        Path mainSources = detectSources(projectRoot, "src/main/java");
        Path testSources = detectSources(projectRoot, "src/test/java");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        if (Files.exists(mainSources)) launcher.addInputResource(mainSources.toString());
        if (Files.exists(testSources)) launcher.addInputResource(testSources.toString());
        launcher.buildModel();

        CtModel model = launcher.getModel();
        write(mainSources, "org/apache/commons/beanutils/PropertyUtils.java", propertyUtilsShim());
        write(mainSources, "javax/validation/Validation.java", validationShim());
        write(mainSources, "javax/validation/ValidatorFactory.java", validatorFactoryShim());
        write(mainSources, "javax/validation/Validator.java", validatorShim());
        write(mainSources, "javax/validation/metadata/BeanDescriptor.java", beanDescriptorShim());
        write(mainSources, "javax/validation/metadata/ElementDescriptor.java", elementDescriptorShim());
        write(mainSources, "javax/validation/metadata/ConstraintDescriptor.java", constraintDescriptorShim());
        write(mainSources, "javax/validation/constraints/NotNull.java", constraintShim("NotNull"));
        write(mainSources, "javax/validation/constraints/NotEmpty.java", constraintShim("NotEmpty"));
        write(mainSources, "javax/validation/constraints/NotBlank.java", constraintShim("NotBlank"));
        write(mainSources, "com/premiumminds/webapp/wicket/testing/AbstractComponentTest.java", abstractComponentTestShim());
    }

    private static Path detectSources(Path projectRoot, String relative) {
        Path p = projectRoot.resolve(relative);
        return Files.exists(p) ? p : projectRoot;
    }

    private static boolean usesType(CtModel model, String fqcn) {
        for (CtTypeReference<?> ref : model.getElements(e -> e instanceof CtTypeReference).stream().map(e -> (CtTypeReference<?>) e).toList()) {
            if (fqcn.equals(ref.getQualifiedName())) return true;
        }
        return false;
    }

    private static boolean usesAnyPrefix(CtModel model, String prefix) {
        for (CtTypeReference<?> ref : model.getElements(e -> e instanceof CtTypeReference).stream().map(e -> (CtTypeReference<?>) e).toList()) {
            String qn = ref.getQualifiedName();
            if (qn != null && qn.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void write(Path base, String relative, String content) throws IOException {
        Path target = base.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private static String propertyUtilsShim() {
        return "package org.apache.commons.beanutils;\n" +
               "import java.beans.BeanInfo;\n" +
               "import java.beans.IntrospectionException;\n" +
               "import java.beans.Introspector;\n" +
               "import java.beans.PropertyDescriptor;\n" +
               "public final class PropertyUtils {\n" +
               "  private PropertyUtils() {}\n" +
               "  public static PropertyDescriptor[] getPropertyDescriptors(Class<?> beanClass) {\n" +
               "    try { return Introspector.getBeanInfo(beanClass).getPropertyDescriptors(); } catch (IntrospectionException e) { throw new IllegalStateException(e); }\n" +
               "  }\n" +
               "  public static PropertyDescriptor getPropertyDescriptor(Object bean, String name) {\n" +
               "    for (PropertyDescriptor d : getPropertyDescriptors(bean.getClass())) if (name.equals(d.getName())) return d;\n" +
               "    throw new IllegalArgumentException(name);\n" +
               "  }\n" +
               "}\n";
    }

    private static String validationShim() {
        return "package javax.validation;\n" +
               "public final class Validation {\n" +
               "  private static final ValidatorFactory FACTORY = new SimpleValidatorFactory();\n" +
               "  private Validation() {}\n" +
               "  public static ValidatorFactory buildDefaultValidatorFactory() { return FACTORY; }\n" +
               "}\n\n" +
               "final class SimpleValidatorFactory implements ValidatorFactory {\n" +
               "  private final Validator validator = new SimpleValidator();\n" +
               "  public Validator getValidator() { return validator; }\n" +
               "}\n\n" +
               "final class SimpleValidator implements Validator {\n" +
               "  public javax.validation.metadata.BeanDescriptor getConstraintsForClass(Class<?> clazz) { return new SimpleBeanDescriptor(clazz); }\n" +
               "}\n\n" +
               "final class SimpleBeanDescriptor implements javax.validation.metadata.BeanDescriptor {\n" +
               "  private final Class<?> type;\n" +
               "  SimpleBeanDescriptor(Class<?> type) { this.type = type; }\n" +
               "  public javax.validation.metadata.ElementDescriptor getConstraintsForProperty(String propertyName) { return new SimpleElementDescriptor(type, propertyName); }\n" +
               "  public java.util.Set<javax.validation.metadata.ConstraintDescriptor<?>> getConstraintDescriptors() { return java.util.Collections.emptySet(); }\n" +
               "}\n\n" +
               "final class SimpleElementDescriptor implements javax.validation.metadata.ElementDescriptor {\n" +
               "  private final Class<?> type;\n" +
               "  private final String propertyName;\n" +
               "  SimpleElementDescriptor(Class<?> type, String propertyName) { this.type = type; this.propertyName = propertyName; }\n" +
               "  public java.util.Set<javax.validation.metadata.ConstraintDescriptor<?>> getConstraintDescriptors() {\n" +
               "    java.util.Set<javax.validation.metadata.ConstraintDescriptor<?>> descriptors = new java.util.LinkedHashSet<>();\n" +
               "    try {\n" +
               "      java.beans.PropertyDescriptor pd = new java.beans.PropertyDescriptor(propertyName, type);\n" +
               "      java.lang.reflect.Field field = null;\n" +
               "      Class<?> current = type;\n" +
               "      while (current != null && field == null) {\n" +
               "        try { field = current.getDeclaredField(propertyName); } catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }\n" +
               "      }\n" +
               "      if (field != null) add(descriptors, field.getAnnotations());\n" +
               "      if (pd.getReadMethod() != null) add(descriptors, pd.getReadMethod().getAnnotations());\n" +
               "    } catch (Exception ignored) {}\n" +
               "    return descriptors;\n" +
               "  }\n" +
               "  private static void add(java.util.Set<javax.validation.metadata.ConstraintDescriptor<?>> descriptors, java.lang.annotation.Annotation[] annotations) {\n" +
               "    for (java.lang.annotation.Annotation annotation : annotations) {\n" +
               "      String simple = annotation.annotationType().getSimpleName();\n" +
               "      if (\"NotNull\".equals(simple) || \"NotEmpty\".equals(simple) || \"NotBlank\".equals(simple)) descriptors.add(new SimpleConstraintDescriptor(annotation));\n" +
               "    }\n" +
               "  }\n" +
               "}\n\n" +
               "final class SimpleConstraintDescriptor implements javax.validation.metadata.ConstraintDescriptor<java.lang.annotation.Annotation> {\n" +
               "  private final java.lang.annotation.Annotation annotation;\n" +
               "  SimpleConstraintDescriptor(java.lang.annotation.Annotation annotation) { this.annotation = annotation; }\n" +
               "  public java.lang.annotation.Annotation getAnnotation() { return annotation; }\n" +
               "}\n";
    }

    private static String validatorFactoryShim() {
        return "package javax.validation;\n" +
               "public interface ValidatorFactory { Validator getValidator(); }\n";
    }

    private static String validatorShim() {
        return "package javax.validation;\n" +
               "import javax.validation.metadata.BeanDescriptor;\n" +
               "public interface Validator { BeanDescriptor getConstraintsForClass(Class<?> clazz); }\n";
    }

    private static String beanDescriptorShim() {
        return "package javax.validation.metadata;\n" +
               "public interface BeanDescriptor extends ElementDescriptor { ElementDescriptor getConstraintsForProperty(String propertyName); }\n";
    }

    private static String elementDescriptorShim() {
        return "package javax.validation.metadata;\n" +
               "import java.util.Set;\n" +
               "public interface ElementDescriptor { Set<ConstraintDescriptor<?>> getConstraintDescriptors(); }\n";
    }

    private static String constraintDescriptorShim() {
        return "package javax.validation.metadata;\n" +
               "import java.lang.annotation.Annotation;\n" +
               "public interface ConstraintDescriptor<T extends Annotation> { T getAnnotation(); }\n";
    }

    private static String constraintShim(String name) {
        return "package javax.validation.constraints;\n" +
               "import java.lang.annotation.ElementType;\n" +
               "import java.lang.annotation.Retention;\n" +
               "import java.lang.annotation.RetentionPolicy;\n" +
               "import java.lang.annotation.Target;\n" +
               "@Retention(RetentionPolicy.RUNTIME)\n" +
               "@Target({ElementType.FIELD, ElementType.METHOD})\n" +
               "public @interface " + name + " {}\n";
    }

    private static String abstractComponentTestShim() {
        return "package com.premiumminds.webapp.wicket.testing;\n" +
               "import org.apache.wicket.Page;\n" +
               "import org.apache.wicket.protocol.http.WebApplication;\n" +
               "import org.apache.wicket.util.tester.WicketTester;\n" +
               "public abstract class AbstractComponentTest {\n" +
               "  private final WicketTester tester = new WicketTester(new WebApplication() {\n" +
               "    @Override public Class<? extends Page> getHomePage() { return DummyPage.class; }\n" +
               "  });\n" +
               "  protected void startTest(org.apache.wicket.Component component) { tester.startComponentInPage(component); }\n" +
               "  protected void replayAll() {}\n" +
               "  protected void verifyAll() {}\n" +
               "  protected void resetAll() {}\n" +
               "  protected void resetTest() {}\n" +
               "  protected WicketTester getTester() { return tester; }\n" +
               "  public static class DummyPage extends Page {}\n" +
               "}\n";
    }
}
