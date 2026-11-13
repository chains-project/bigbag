package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Map<String, String> TARGETS = new HashMap<>();

    static {
        TARGETS.put("allOf", "org.hamcrest.CoreMatchers");
        TARGETS.put("any", "org.hamcrest.CoreMatchers");
        TARGETS.put("anyOf", "org.hamcrest.CoreMatchers");
        TARGETS.put("anything", "org.hamcrest.CoreMatchers");
        TARGETS.put("both", "org.hamcrest.CoreMatchers");
        TARGETS.put("containsString", "org.hamcrest.CoreMatchers");
        TARGETS.put("containsStringIgnoringCase", "org.hamcrest.CoreMatchers");
        TARGETS.put("describedAs", "org.hamcrest.CoreMatchers");
        TARGETS.put("either", "org.hamcrest.CoreMatchers");
        TARGETS.put("endsWith", "org.hamcrest.CoreMatchers");
        TARGETS.put("endsWithIgnoringCase", "org.hamcrest.CoreMatchers");
        TARGETS.put("equalTo", "org.hamcrest.CoreMatchers");
        TARGETS.put("equalToObject", "org.hamcrest.CoreMatchers");
        TARGETS.put("everyItem", "org.hamcrest.CoreMatchers");
        TARGETS.put("hasItem", "org.hamcrest.CoreMatchers");
        TARGETS.put("hasItems", "org.hamcrest.CoreMatchers");
        TARGETS.put("instanceOf", "org.hamcrest.CoreMatchers");
        TARGETS.put("is", "org.hamcrest.CoreMatchers");
        TARGETS.put("not", "org.hamcrest.CoreMatchers");
        TARGETS.put("notNullValue", "org.hamcrest.CoreMatchers");
        TARGETS.put("nullValue", "org.hamcrest.CoreMatchers");
        TARGETS.put("sameInstance", "org.hamcrest.CoreMatchers");
        TARGETS.put("startsWith", "org.hamcrest.CoreMatchers");
        TARGETS.put("startsWithIgnoringCase", "org.hamcrest.CoreMatchers");
        TARGETS.put("theInstance", "org.hamcrest.CoreMatchers");

        TARGETS.put("contains", "org.hamcrest.collection.IsIterableContainingInOrder");
        TARGETS.put("containsInAnyOrder", "org.hamcrest.collection.IsIterableContainingInAnyOrder");
        TARGETS.put("containsInRelativeOrder", "org.hamcrest.collection.IsIterableContainingInRelativeOrder");
        TARGETS.put("empty", "org.hamcrest.collection.IsEmptyCollection");
        TARGETS.put("emptyCollectionOf", "org.hamcrest.collection.IsEmptyCollection");
        TARGETS.put("emptyIterable", "org.hamcrest.collection.IsEmptyIterable");
        TARGETS.put("emptyIterableOf", "org.hamcrest.collection.IsEmptyIterable");
        TARGETS.put("hasEntry", "org.hamcrest.collection.IsMapContaining");
        TARGETS.put("hasKey", "org.hamcrest.collection.IsMapContaining");
        TARGETS.put("hasSize", "org.hamcrest.collection.IsCollectionWithSize");
        TARGETS.put("iterableWithSize", "org.hamcrest.collection.IsIterableWithSize");
        TARGETS.put("hasValue", "org.hamcrest.collection.IsMapContaining");

        TARGETS.put("hasProperty", "org.hamcrest.beans.HasPropertyWithValue");
        TARGETS.put("hasPropertyAtPath", "org.hamcrest.beans.HasPropertyWithValue");
        TARGETS.put("samePropertyValuesAs", "org.hamcrest.beans.SamePropertyValuesAs");

        TARGETS.put("blankOrNullString", "org.hamcrest.text.IsBlankString");
        TARGETS.put("blankString", "org.hamcrest.text.IsBlankString");
        TARGETS.put("emptyOrNullString", "org.hamcrest.text.IsEmptyString");
        TARGETS.put("emptyString", "org.hamcrest.text.IsEmptyString");
        TARGETS.put("equalToCompressingWhiteSpace", "org.hamcrest.text.IsEqualCompressingWhiteSpace");
        TARGETS.put("equalToIgnoringCase", "org.hamcrest.text.IsEqualIgnoringCase");
        TARGETS.put("equalToIgnoringWhiteSpace", "org.hamcrest.text.IsEqualCompressingWhiteSpace");
        TARGETS.put("isEmptyOrNullString", "org.hamcrest.text.IsEmptyString");
        TARGETS.put("isEmptyString", "org.hamcrest.text.IsEmptyString");
        TARGETS.put("matchesPattern", "org.hamcrest.text.MatchesPattern");
        TARGETS.put("matchesRegex", "org.hamcrest.text.MatchesPattern");
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("usage: Main <source-dir> [output-dir]");
        }
        final Path input = Paths.get(args[0]);
        final Path output = args.length > 1 ? Paths.get(args[1]) : input;
        final Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setComplianceLevel(17);
        addJavaSources(launcher, input);
        launcher.setSourceOutputDirectory(output.toString());
        launcher.buildModel();
        for (CtInvocation<?> invocation : launcher.getModel().getElements(
            new TypeFilter<>(CtInvocation.class)
        )) {
            rewrite(invocation);
        }
        launcher.prettyprint();
    }

    private static void rewrite(final CtInvocation<?> invocation) {
        final CtElement target = invocation.getTarget();
        if (!(target instanceof CtTypeAccess<?>)) {
            return;
        }
        final CtTypeAccess<?> access = (CtTypeAccess<?>) target;
        final CtTypeReference<?> type = access.getAccessedType();
        if (type == null || !"org.hamcrest.Matchers".equals(type.getQualifiedName())) {
            return;
        }
        final String method = invocation.getExecutable().getSimpleName();
        final String replacement = TARGETS.get(method);
        if (replacement == null) {
            return;
        }
        access.setAccessedType(invocation.getFactory().Type().createReference(replacement));
    }

    private static void addJavaSources(final Launcher launcher, final Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (final IOException ex) {
            throw new IllegalStateException("cannot read sources from " + root, ex);
        }
    }
}
