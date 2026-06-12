package github.chains;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;

public class Main {

    private static final String OLD_MATCHERS = "org.hamcrest.Matchers";
    private static final String NEW_MATCHERS = "org.hamcrest.CoreMatchers";

    private static final Map<String, String> OWNER_BY_METHOD = new HashMap<>();

    static {
        OWNER_BY_METHOD.put("allOf", NEW_MATCHERS);
        OWNER_BY_METHOD.put("anyOf", NEW_MATCHERS);
        OWNER_BY_METHOD.put("both", NEW_MATCHERS);
        OWNER_BY_METHOD.put("either", NEW_MATCHERS);
        OWNER_BY_METHOD.put("describedAs", NEW_MATCHERS);
        OWNER_BY_METHOD.put("everyItem", NEW_MATCHERS);
        OWNER_BY_METHOD.put("is", NEW_MATCHERS);
        OWNER_BY_METHOD.put("any", "org.hamcrest.core.IsInstanceOf");
        OWNER_BY_METHOD.put("instanceOf", "org.hamcrest.core.IsInstanceOf");
        OWNER_BY_METHOD.put("anything", "org.hamcrest.core.IsNull");
        OWNER_BY_METHOD.put("equalTo", "org.hamcrest.core.IsEqual");
        OWNER_BY_METHOD.put("equalToObject", "org.hamcrest.core.IsEqual");
        OWNER_BY_METHOD.put("not", "org.hamcrest.core.IsNot");
        OWNER_BY_METHOD.put("notNullValue", "org.hamcrest.core.IsNull");
        OWNER_BY_METHOD.put("nullValue", "org.hamcrest.core.IsNull");
        OWNER_BY_METHOD.put("sameInstance", "org.hamcrest.core.IsSame");
        OWNER_BY_METHOD.put("theInstance", "org.hamcrest.core.IsSame");
        OWNER_BY_METHOD.put("containsString", "org.hamcrest.core.StringContains");
        OWNER_BY_METHOD.put("containsStringIgnoringCase", "org.hamcrest.core.StringContains");
        OWNER_BY_METHOD.put("startsWith", "org.hamcrest.core.StringStartsWith");
        OWNER_BY_METHOD.put("startsWithIgnoringCase", "org.hamcrest.core.StringStartsWith");
        OWNER_BY_METHOD.put("endsWith", "org.hamcrest.core.StringEndsWith");
        OWNER_BY_METHOD.put("endsWithIgnoringCase", "org.hamcrest.core.StringEndsWith");
        OWNER_BY_METHOD.put("matchesPattern", "org.hamcrest.text.MatchesPattern");
        OWNER_BY_METHOD.put("hasLength", "org.hamcrest.text.CharSequenceLength");
        OWNER_BY_METHOD.put("hasToString", "org.hamcrest.object.HasToString");
        OWNER_BY_METHOD.put("hasProperty", "org.hamcrest.beans.HasProperty");
        OWNER_BY_METHOD.put("samePropertyValuesAs", "org.hamcrest.beans.SamePropertyValuesAs");
        OWNER_BY_METHOD.put("hasEntry", "org.hamcrest.collection.IsMapContaining");
        OWNER_BY_METHOD.put("hasKey", "org.hamcrest.collection.IsMapContaining");
        OWNER_BY_METHOD.put("hasValue", "org.hamcrest.collection.IsMapContaining");
        OWNER_BY_METHOD.put("hasSize", "org.hamcrest.collection.IsCollectionWithSize");
        OWNER_BY_METHOD.put("iterableWithSize", "org.hamcrest.collection.IsIterableWithSize");
        OWNER_BY_METHOD.put("empty", "org.hamcrest.collection.IsEmptyCollection");
        OWNER_BY_METHOD.put("emptyCollectionOf", "org.hamcrest.collection.IsEmptyCollection");
        OWNER_BY_METHOD.put("emptyIterable", "org.hamcrest.collection.IsEmptyIterable");
        OWNER_BY_METHOD.put("emptyIterableOf", "org.hamcrest.collection.IsEmptyIterable");
        OWNER_BY_METHOD.put("contains", "org.hamcrest.collection.IsIterableContainingInOrder");
        OWNER_BY_METHOD.put("containsInAnyOrder", "org.hamcrest.collection.IsIterableContainingInAnyOrder");
        OWNER_BY_METHOD.put("containsInRelativeOrder", "org.hamcrest.collection.IsIterableContainingInRelativeOrder");
        OWNER_BY_METHOD.put("hasItem", "org.hamcrest.collection.IsIterableContaining");
        OWNER_BY_METHOD.put("hasItems", "org.hamcrest.collection.IsIterableContaining");
        OWNER_BY_METHOD.put("isIn", "org.hamcrest.collection.IsIn");
        OWNER_BY_METHOD.put("in", "org.hamcrest.collection.IsIn");
        OWNER_BY_METHOD.put("oneOf", "org.hamcrest.collection.IsIn");
        OWNER_BY_METHOD.put("isOneOf", "org.hamcrest.collection.IsIn");
        OWNER_BY_METHOD.put("array", "org.hamcrest.collection.IsArray");
        OWNER_BY_METHOD.put("arrayContaining", "org.hamcrest.collection.IsArrayContainingInOrder");
        OWNER_BY_METHOD.put("arrayContainingInAnyOrder", "org.hamcrest.collection.IsArrayContainingInAnyOrder");
        OWNER_BY_METHOD.put("arrayWithSize", "org.hamcrest.collection.IsArrayWithSize");
        OWNER_BY_METHOD.put("emptyArray", "org.hamcrest.collection.IsArrayWithSize");
        OWNER_BY_METHOD.put("hasItemInArray", "org.hamcrest.collection.HasItemInArray");
        OWNER_BY_METHOD.put("blankString", "org.hamcrest.text.IsBlankString");
        OWNER_BY_METHOD.put("blankOrNullString", "org.hamcrest.text.IsBlankString");
        OWNER_BY_METHOD.put("emptyString", "org.hamcrest.text.IsEmptyString");
        OWNER_BY_METHOD.put("isEmptyString", "org.hamcrest.text.IsEmptyString");
        OWNER_BY_METHOD.put("emptyOrNullString", "org.hamcrest.text.IsEmptyString");
        OWNER_BY_METHOD.put("isEmptyOrNullString", "org.hamcrest.text.IsEmptyString");
        OWNER_BY_METHOD.put("equalToIgnoringCase", "org.hamcrest.text.IsEqualIgnoringCase");
        OWNER_BY_METHOD.put("equalToIgnoringWhiteSpace", "org.hamcrest.text.IsEqualCompressingWhiteSpace");
        OWNER_BY_METHOD.put("equalToCompressingWhiteSpace", "org.hamcrest.text.IsEqualCompressingWhiteSpace");
        OWNER_BY_METHOD.put("closeTo", "org.hamcrest.number.IsCloseTo");
        OWNER_BY_METHOD.put("notANumber", "org.hamcrest.number.IsNaN");
        OWNER_BY_METHOD.put("comparesEqualTo", "org.hamcrest.number.OrderingComparison");
        OWNER_BY_METHOD.put("greaterThan", "org.hamcrest.number.OrderingComparison");
        OWNER_BY_METHOD.put("greaterThanOrEqualTo", "org.hamcrest.number.OrderingComparison");
        OWNER_BY_METHOD.put("lessThan", "org.hamcrest.number.OrderingComparison");
        OWNER_BY_METHOD.put("lessThanOrEqualTo", "org.hamcrest.number.OrderingComparison");
        OWNER_BY_METHOD.put("hasXPath", "org.hamcrest.xml.HasXPath");
        OWNER_BY_METHOD.put("stringContainsInOrder", "org.hamcrest.text.StringContainsInOrder");
        OWNER_BY_METHOD.put("hasXPaths", "org.hamcrest.xml.HasXPath");
    }

    public static void main(final String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> <output-source-dir>");
        }
        final Launcher launcher = new Launcher();
        launcher.addInputResource(args[0]);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.setSourceOutputDirectory(new File(args[1]));
        launcher.buildModel();

        for (final CtType<?> type : launcher.getFactory().Class().getAll()) {
            rewriteTypeImports(type);
            for (final CtInvocation<?> invocation : type.getElements((CtInvocation<?> inv) -> true)) {
                rewriteInvocation(invocation);
            }
        }

        launcher.prettyprint();
    }

    private static void rewriteTypeImports(final CtType<?> type) {
        final List<CtImport> imports = type.getFactory().CompilationUnit().getOrCreate(type).getImports();
        for (final CtImport imprt : imports) {
            if (imprt.getReference() instanceof CtTypeReference) {
                final CtTypeReference<?> reference = (CtTypeReference<?>) imprt.getReference();
                if (OLD_MATCHERS.equals(reference.getQualifiedName())) {
                    imprt.setReference(type.getFactory().Type().createReference(NEW_MATCHERS));
                }
            }
        }
    }

    private static void rewriteInvocation(final CtInvocation<?> invocation) {
        final CtExecutableReference<?> executable = invocation.getExecutable();
        if (executable == null || executable.getDeclaringType() == null) {
            return;
        }
        if (!OLD_MATCHERS.equals(executable.getDeclaringType().getQualifiedName())) {
            return;
        }
        final String owner = OWNER_BY_METHOD.get(executable.getSimpleName());
        if (owner == null) {
            return;
        }
        executable.setDeclaringType(invocation.getFactory().Type().createReference(owner));
    }
}
