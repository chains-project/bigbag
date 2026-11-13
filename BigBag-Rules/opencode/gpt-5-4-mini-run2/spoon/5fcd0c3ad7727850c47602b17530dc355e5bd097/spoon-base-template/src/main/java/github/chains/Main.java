package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Main {

    private static final String COVERAGE_DATABASE = "org.pitest.coverage.CoverageDatabase";
    private static final String CLASS_INFO = "org.pitest.classinfo.ClassInfo";
    private static final String CLASS_NAME = "org.pitest.classinfo.ClassName";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <sourceRoot> [outputRoot]");
        }

        final Path inputRoot = Paths.get(args[0]);
        final Path outputRoot = args.length > 1 ? Paths.get(args[1]) : inputRoot;

        final Launcher launcher = new Launcher();
        launcher.addInputResource(inputRoot.toString());
        launcher.setSourceOutputDirectory(outputRoot.toFile());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setPreserveLineNumbers(true);
        launcher.buildModel();

        transform(launcher.getFactory());
        launcher.prettyprint();
    }

    private static void transform(final Factory factory) {
        replaceRemovedCoverageLookup(factory);
        replaceClassInfoCollectionTypes(factory);
        replaceClassInfoAccessors(factory);
    }

    private static void replaceRemovedCoverageLookup(final Factory factory) {
        for (final CtInvocation<?> invocation : factory.getModel()
            .getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!invocation.getExecutable().getSimpleName().equals("getClassInfo")) {
                continue;
            }
            if (invocation.getExecutable().getDeclaringType() == null) {
                continue;
            }
            if (!COVERAGE_DATABASE.equals(
                invocation.getExecutable().getDeclaringType().getQualifiedName())) {
                continue;
            }
            if (invocation.getArguments().size() != 1) {
                continue;
            }
            invocation.replace(invocation.getArguments().get(0).clone());
        }
    }

    private static void replaceClassInfoCollectionTypes(final Factory factory) {
        for (final CtType<?> type : factory.getModel().getElements(new TypeFilter<>(CtType.class))) {
            for (final CtField<?> field : type.getFields()) {
                rewriteCollectionElementType(field.getType());
            }
            for (final CtMethod<?> method : type.getMethods()) {
                rewriteCollectionElementType(method.getType());
                for (final CtParameter<?> parameter : method.getParameters()) {
                    rewriteCollectionElementType(parameter.getType());
                }
            }
        }
    }

    private static void rewriteCollectionElementType(final CtTypeReference<?> type) {
        if (type == null || type.getActualTypeArguments().isEmpty()) {
            return;
        }

        final List<CtTypeReference<?>> arguments = new ArrayList<>(type.getActualTypeArguments());
        boolean changed = false;
        for (int i = 0; i < arguments.size(); i++) {
            final CtTypeReference<?> argument = arguments.get(i);
            if (argument != null && CLASS_INFO.equals(argument.getQualifiedName()) && isCollectionLike(type)) {
                arguments.set(i, type.getFactory().createReference(CLASS_NAME));
                changed = true;
            }
        }
        if (changed) {
            type.setActualTypeArguments(arguments);
        }
    }

    private static boolean isCollectionLike(final CtTypeReference<?> type) {
        if (type == null || type.getQualifiedName() == null) {
            return false;
        }
        final String qName = type.getQualifiedName();
        return qName.equals("java.util.Collection")
            || qName.equals("java.util.List")
            || qName.equals("java.util.Set")
            || qName.equals("java.util.Iterable")
            || qName.equals("java.util.Queue")
            || qName.equals("java.util.Deque")
            || qName.equals("java.util.ArrayList")
            || qName.equals("java.util.HashSet")
            || qName.equals("java.util.LinkedHashSet")
            || qName.equals("java.util.TreeSet")
            || qName.equals("java.util.LinkedList");
    }

    private static void replaceClassInfoAccessors(final Factory factory) {
        for (final CtInvocation<?> invocation : factory.getModel()
            .getElements(new TypeFilter<>(CtInvocation.class))) {
            if (!invocation.getExecutable().getSimpleName().equals("getName")) {
                continue;
            }
            if (invocation.getTarget() == null || invocation.getTarget().getType() == null) {
                continue;
            }
            if (!CLASS_INFO.equals(invocation.getTarget().getType().getQualifiedName())) {
                continue;
            }
            invocation.replace(invocation.getTarget().clone());
        }
    }
}
