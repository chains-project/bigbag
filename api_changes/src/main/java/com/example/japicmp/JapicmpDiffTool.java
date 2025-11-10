package com.example.japicmp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.config.Options;
import japicmp.model.JApiAnnotation;
import japicmp.model.JApiAnnotationElement;
import japicmp.model.JApiAnnotationElementValue;
import japicmp.model.JApiBehavior;
import japicmp.model.JApiChangeStatus;
import japicmp.model.JApiClass;
import japicmp.model.JApiClassFileFormatVersion;
import japicmp.model.JApiCompatibilityChange;
import japicmp.model.JApiConstructor;
import japicmp.model.JApiField;
import japicmp.model.JApiImplementedInterface;
import japicmp.model.JApiMethod;
import japicmp.model.JApiModifier;
import japicmp.model.JApiParameter;
import japicmp.model.JApiReturnType;
import japicmp.model.JApiType;
import japicmp.model.AccessModifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Command line tool that compares two JAR files using japicmp and emits an enriched JSON report.
 */
public final class JapicmpDiffTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.INDENT_OUTPUT, true);

    private JapicmpDiffTool() {
        // utility class
    }

    public static void main(String[] args) {
        if (args.length < 2 || args.length > 3) {
            System.err.println("Usage: java -jar japicmp-diff-tool.jar <old-jar> <new-jar> [output-json]");
            System.exit(1);
        }

        Path oldJar = Path.of(args[0]).toAbsolutePath().normalize();
        Path newJar = Path.of(args[1]).toAbsolutePath().normalize();
        Path outputJson = args.length == 3
                ? Path.of(args[2]).toAbsolutePath().normalize()
                : Path.of("japicmp-report.json").toAbsolutePath();

        validateJarPath(oldJar, "old");
        validateJarPath(newJar, "new");

        try {
            ComparisonReport report = generateComparisonReport(oldJar, newJar);
            writeReport(report, outputJson);
            System.out.printf("Report written to %s with %d changed classes.%n",
                    outputJson, report.summary.changedClasses);
        } catch (Exception ex) {
            System.err.printf("Failed to generate report: %s%n", ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(2);
        }
    }

    private static void validateJarPath(Path jarPath, String label) {
        if (!Files.exists(jarPath)) {
            throw new IllegalArgumentException("Cannot find " + label + " JAR: " + jarPath);
        }
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalArgumentException("Path for " + label + " JAR is not a regular file: " + jarPath);
        }
    }

    public static ComparisonReport generateComparisonReport(Path oldJar, Path newJar) {
        Options options = Options.newDefault();
        options.setAccessModifier(AccessModifier.PRIVATE);
        options.setIncludeSynthetic(true);
        options.setIgnoreMissingClasses(true);

        JarArchiveComparatorOptions comparatorOptions = JarArchiveComparatorOptions.of(options);
        comparatorOptions.getIgnoreMissingClasses().setIgnoreAllMissingClasses(true);

        JarArchiveComparator comparator = new JarArchiveComparator(comparatorOptions);

        JApiCmpArchive oldArchive = new JApiCmpArchive(oldJar.toFile(), "old");
        JApiCmpArchive newArchive = new JApiCmpArchive(newJar.toFile(), "new");

        List<JApiClass> allClasses = comparator.compare(oldArchive, newArchive);

        List<ClassChange> classChanges = allClasses.stream()
                .sorted(Comparator.comparing(JApiClass::getFullyQualifiedName))
                .map(JapicmpDiffTool::mapClassChange)
                .collect(Collectors.toList());

        long changedMembers = allClasses.stream()
                .mapToLong(JapicmpDiffTool::countChangedMembers)
                .sum();

        long changedClassesCount = allClasses.stream()
                .filter(JapicmpDiffTool::hasRelevantChange)
                .count();

        Summary summary = new Summary(
                allClasses.size(),
                changedClassesCount,
                changedMembers
        );

        return new ComparisonReport(
                oldJar.toString(),
                newJar.toString(),
                Instant.now().toString(),
                summary,
                classChanges
        );
    }

    private static long countChangedMembers(JApiClass jApiClass) {
        long methods = jApiClass.getMethods().stream().filter(JapicmpDiffTool::hasBehaviorChange).count();
        long constructors = jApiClass.getConstructors().stream().filter(JapicmpDiffTool::hasBehaviorChange).count();
        long fields = jApiClass.getFields().stream().filter(JapicmpDiffTool::hasFieldChange).count();
        return methods + constructors + fields;
    }

    private static void writeReport(ComparisonReport report, Path outputJson) throws IOException {
        Path parent = outputJson.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        OBJECT_MAPPER.writeValue(outputJson.toFile(), report);
    }

    private static boolean hasRelevantChange(JApiClass jApiClass) {
        boolean memberChanges = jApiClass.getMethods().stream().anyMatch(JapicmpDiffTool::hasBehaviorChange)
                || jApiClass.getConstructors().stream().anyMatch(JapicmpDiffTool::hasBehaviorChange)
                || jApiClass.getFields().stream().anyMatch(JapicmpDiffTool::hasFieldChange);

        if (memberChanges) {
            return true;
        }

        if (jApiClass.getChangeStatus() != JApiChangeStatus.UNCHANGED) {
            return true;
        }

        boolean modifiersChanged = hasModifierChange(jApiClass.getModifiers());
        boolean annotationsChanged = jApiClass.getAnnotations().stream()
                .anyMatch(a -> a.getChangeStatus() != JApiChangeStatus.UNCHANGED);
        boolean superclassChanged = jApiClass.getSuperclass().getChangeStatus() != JApiChangeStatus.UNCHANGED
                && !Objects.equals(jApiClass.getSuperclass().getOldSuperclassName().orElse(null),
                jApiClass.getSuperclass().getNewSuperclassName().orElse(null));
        boolean interfacesChanged = jApiClass.getInterfaces().stream()
                .anyMatch(i -> i.getChangeStatus() != JApiChangeStatus.UNCHANGED);
        boolean classFileVersionChanged = jApiClass.getClassFileFormatVersion().getChangeStatus() != JApiChangeStatus.UNCHANGED;

        return modifiersChanged || annotationsChanged || superclassChanged || interfacesChanged || classFileVersionChanged;
    }

    private static boolean hasBehaviorChange(JApiBehavior behavior) {
        if (behavior.getChangeStatus() != JApiChangeStatus.UNCHANGED) {
            return true;
        }
        if (!behavior.getCompatibilityChanges().isEmpty()) {
            return true;
        }
        if (hasModifierChange(behavior.getModifiers())) {
            return true;
        }
        return behavior.getAnnotations().stream().anyMatch(a -> a.getChangeStatus() != JApiChangeStatus.UNCHANGED);
    }

    private static boolean hasFieldChange(JApiField field) {
        if (field.getChangeStatus() != JApiChangeStatus.UNCHANGED) {
            return true;
        }
        if (!field.getCompatibilityChanges().isEmpty()) {
            return true;
        }
        if (hasModifierChange(field.getModifiers())) {
            return true;
        }
        if (field.getType().getChangeStatus() != JApiChangeStatus.UNCHANGED) {
            return true;
        }
        return field.getAnnotations().stream().anyMatch(a -> a.getChangeStatus() != JApiChangeStatus.UNCHANGED);
    }

    private static boolean hasModifierChange(List<? extends JApiModifier<? extends Enum<?>>> modifiers) {
        return modifiers.stream().anyMatch(modifier -> modifier.getChangeStatus() != JApiChangeStatus.UNCHANGED);
    }

    private static ClassChange mapClassChange(JApiClass jApiClass) {
        String fqn = jApiClass.getFullyQualifiedName();
        String packageName = derivePackageName(fqn);
        String simpleName = deriveSimpleName(fqn);

        ClassDetail detail = new ClassDetail(
                jApiClass.getClassType().getNewTypeOptional().map(Enum::name).orElse(null),
                toValueChange(jApiClass.getSuperclass().getOldSuperclassName(), jApiClass.getSuperclass().getNewSuperclassName()),
                mapInterfaces(jApiClass.getInterfaces()),
                mapClassFileVersion(jApiClass.getClassFileFormatVersion()),
                collectModifierStrings(jApiClass.getModifiers(), false),
                collectModifierStrings(jApiClass.getModifiers(), true),
                mapAnnotations(jApiClass.getAnnotations()),
                mapConstructors(jApiClass.getConstructors()),
                mapMethods(jApiClass.getMethods()),
                mapFields(jApiClass.getFields())
        );

        int changedMembers = (int) countChangedMembers(jApiClass);

        return new ClassChange(
                "CLASS",
                fqn,
                simpleName,
                packageName,
                jApiClass.getChangeStatus().name(),
                jApiClass.isBinaryCompatible(),
                jApiClass.isSourceCompatible(),
                mapCompatibilityChanges(jApiClass.getCompatibilityChanges()),
                changedMembers,
                detail
        );
    }

    private static String derivePackageName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot < 0) {
            return "";
        }
        return fullyQualifiedName.substring(0, lastDot);
    }

    private static String deriveSimpleName(String fullyQualifiedName) {
        int lastDot = fullyQualifiedName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fullyQualifiedName.length() - 1) {
            return fullyQualifiedName;
        }
        return fullyQualifiedName.substring(lastDot + 1);
    }

    private static ValueChange<String> mapClassFileVersion(JApiClassFileFormatVersion version) {
        if (version == null) {
            return new ValueChange<>(null, null, false);
        }
        String oldVersion = version.getMajorVersionOld() == 0 && version.getMinorVersionOld() == 0
                ? null
                : version.getMajorVersionOld() + "." + version.getMinorVersionOld();
        String newVersion = version.getMajorVersionNew() == 0 && version.getMinorVersionNew() == 0
                ? null
                : version.getMajorVersionNew() + "." + version.getMinorVersionNew();
        boolean changed = version.getChangeStatus() != JApiChangeStatus.UNCHANGED;
        return new ValueChange<>(oldVersion, newVersion, changed);
    }

    private static List<InterfaceChange> mapInterfaces(List<JApiImplementedInterface> interfaces) {
        return interfaces.stream()
                .map(intf -> new InterfaceChange(
                        intf.getFullyQualifiedName(),
                        intf.getChangeStatus().name(),
                        intf.isBinaryCompatible(),
                        intf.isSourceCompatible(),
                        mapCompatibilityChanges(intf.getCompatibilityChanges())
                ))
                .sorted(Comparator.comparing((InterfaceChange change) -> change.name))
                .collect(Collectors.toList());
    }

    private static List<MemberChange> mapConstructors(List<JApiConstructor> constructors) {
        return constructors.stream()
                .map(constructor -> new MemberChange(
                        "CONSTRUCTOR",
                        constructor.getName(),
                        constructor.getChangeStatus().name(),
                        constructor.isBinaryCompatible(),
                        constructor.isSourceCompatible(),
                        mapCompatibilityChanges(constructor.getCompatibilityChanges()),
                        toValueChange(constructor.getOldConstructor().map(Object::toString),
                                constructor.getNewConstructor().map(Object::toString)),
                        ValueChange.empty(),
                        collectModifierStrings(constructor.getModifiers(), false),
                        collectModifierStrings(constructor.getModifiers(), true),
                        mapAnnotations(constructor.getAnnotations()),
                        constructor.getParameters().stream().map(JApiParameter::getType).collect(Collectors.toList())
                ))
                .sorted(Comparator.comparing((MemberChange change) -> change.name))
                .collect(Collectors.toList());
    }

    private static List<MemberChange> mapMethods(List<JApiMethod> methods) {
        return methods.stream()
                .map(method -> {
                    ValueChange<String> signature = toValueChange(
                            method.getOldMethod().map(Object::toString),
                            method.getNewMethod().map(Object::toString)
                    );
                    JApiReturnType returnType = method.getReturnType();
                    ValueChange<String> returnTypeChange = new ValueChange<>(
                            optionalString(returnType.getOldReturnType()),
                            optionalString(returnType.getNewReturnType()),
                            returnType.getChangeStatus() != JApiChangeStatus.UNCHANGED
                    );
                    return new MemberChange(
                            "METHOD",
                            method.getName(),
                            method.getChangeStatus().name(),
                            method.isBinaryCompatible(),
                            method.isSourceCompatible(),
                            mapCompatibilityChanges(method.getCompatibilityChanges()),
                            signature,
                            returnTypeChange,
                            collectModifierStrings(method.getModifiers(), false),
                            collectModifierStrings(method.getModifiers(), true),
                            mapAnnotations(method.getAnnotations()),
                            method.getParameters().stream().map(JApiParameter::getType).collect(Collectors.toList())
                    );
                })
                .sorted(Comparator.comparing((MemberChange change) -> change.name))
                .collect(Collectors.toList());
    }

    private static List<MemberChange> mapFields(List<JApiField> fields) {
        return fields.stream()
                .map(field -> {
                    JApiType type = field.getType();
                    ValueChange<String> typeChange = toValueChange(
                            type.getOldTypeOptional(),
                            type.getNewTypeOptional()
                    );
                    ValueChange<String> signature = new ValueChange<>(
                            field.getOldFieldOptional().map(Object::toString).orElse(null),
                            field.getNewFieldOptional().map(Object::toString).orElse(null),
                            field.getChangeStatus() != JApiChangeStatus.UNCHANGED
                    );
                    return new MemberChange(
                            "FIELD",
                            field.getName(),
                            field.getChangeStatus().name(),
                            field.isBinaryCompatible(),
                            field.isSourceCompatible(),
                            mapCompatibilityChanges(field.getCompatibilityChanges()),
                            signature,
                            typeChange,
                            collectModifierStrings(field.getModifiers(), false),
                            collectModifierStrings(field.getModifiers(), true),
                            mapAnnotations(field.getAnnotations()),
                            List.of()
                    );
                })
                .sorted(Comparator.comparing((MemberChange change) -> change.name))
                .collect(Collectors.toList());
    }

    private static List<AnnotationDetail> mapAnnotations(List<JApiAnnotation> annotations) {
        return annotations.stream()
                .map(annotation -> new AnnotationDetail(
                        annotation.getFullyQualifiedName(),
                        annotation.getChangeStatus().name(),
                        annotation.isBinaryCompatible(),
                        annotation.isSourceCompatible(),
                        mapCompatibilityChanges(annotation.getCompatibilityChanges()),
                        mapAnnotationElements(annotation.getElements())
                ))
                .sorted(Comparator.comparing((AnnotationDetail detail) -> detail.name))
                .collect(Collectors.toList());
    }

    private static List<AnnotationElementDetail> mapAnnotationElements(List<JApiAnnotationElement> elements) {
        return elements.stream()
                .map(element -> new AnnotationElementDetail(
                        element.getName(),
                        element.getChangeStatus().name(),
                        mapAnnotationElementValues(element.getOldElementValues()),
                        mapAnnotationElementValues(element.getNewElementValues()),
                        mapCompatibilityChanges(element.getCompatibilityChanges())
                ))
                .sorted(Comparator.comparing((AnnotationElementDetail detail) -> detail.name))
                .collect(Collectors.toList());
    }

    private static List<String> mapAnnotationElementValues(List<JApiAnnotationElementValue> values) {
        return values.stream()
                .map(JApiAnnotationElementValue::getValueString)
                .map(value -> value == null ? null : value.trim())
                .collect(Collectors.toList());
    }

    private static List<CompatibilityChangeInfo> mapCompatibilityChanges(List<JApiCompatibilityChange> changes) {
        return changes.stream()
                .map(change -> new CompatibilityChangeInfo(
                        change.getType().name(),
                        change.isBinaryCompatible(),
                        change.isSourceCompatible(),
                        change.getSemanticVersionLevel().name()
                ))
                .collect(Collectors.toList());
    }

    private static ValueChange<String> toValueChange(Optional<String> oldValue, Optional<String> newValue) {
        String oldVal = oldValue.orElse(null);
        String newVal = newValue.orElse(null);
        boolean changed = !Objects.equals(oldVal, newVal);
        return new ValueChange<>(oldVal, newVal, changed);
    }

    private static List<String> collectModifierStrings(List<? extends JApiModifier<? extends Enum<?>>> modifiers, boolean useNew) {
        return modifiers.stream()
                .map(modifier -> useNew ? modifier.getNewModifier().map(Object::toString) : modifier.getOldModifier().map(Object::toString))
                .flatMap(Optional::stream)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static String optionalString(String value) {
        return value == null || value.isBlank() ? null : value;
    }


    @SuppressWarnings("unused")
    public static final class ComparisonReport {
        public final String oldJar;
        public final String newJar;
        public final String generatedAt;
        public final Summary summary;
        public final List<ClassChange> changes;

        private ComparisonReport(String oldJar,
                                 String newJar,
                                 String generatedAt,
                                 Summary summary,
                                 List<ClassChange> changes) {
            this.oldJar = oldJar;
            this.newJar = newJar;
            this.generatedAt = generatedAt;
            this.summary = summary;
            this.changes = List.copyOf(changes);
        }
    }

    @SuppressWarnings("unused")
    public static final class Summary {
        public final long totalClasses;
        public final long changedClasses;
        public final long changedMembers;

        private Summary(long totalClasses, long changedClasses, long changedMembers) {
            this.totalClasses = totalClasses;
            this.changedClasses = changedClasses;
            this.changedMembers = changedMembers;
        }
    }

    @SuppressWarnings("unused")
    public static final class ClassChange {
        public final String elementType;
        public final String fullyQualifiedName;
        public final String simpleName;
        public final String packageName;
        public final String changeStatus;
        public final boolean binaryCompatible;
        public final boolean sourceCompatible;
        public final List<CompatibilityChangeInfo> compatibilityChanges;
        public final int changedMemberCount;
        public final ClassDetail detail;

        private ClassChange(String elementType,
                            String fullyQualifiedName,
                            String simpleName,
                            String packageName,
                            String changeStatus,
                            boolean binaryCompatible,
                            boolean sourceCompatible,
                            List<CompatibilityChangeInfo> compatibilityChanges,
                            int changedMemberCount,
                            ClassDetail detail) {
            this.elementType = elementType;
            this.fullyQualifiedName = fullyQualifiedName;
            this.simpleName = simpleName;
            this.packageName = packageName;
            this.changeStatus = changeStatus;
            this.binaryCompatible = binaryCompatible;
            this.sourceCompatible = sourceCompatible;
            this.compatibilityChanges = List.copyOf(compatibilityChanges);
            this.changedMemberCount = changedMemberCount;
            this.detail = detail;
        }
    }

    @SuppressWarnings("unused")
    public static final class ClassDetail {
        public final String classType;
        public final ValueChange<String> superclass;
        public final List<InterfaceChange> interfaces;
        public final ValueChange<String> classFileVersion;
        public final List<String> oldModifiers;
        public final List<String> newModifiers;
        public final List<AnnotationDetail> annotations;
        public final List<MemberChange> constructors;
        public final List<MemberChange> methods;
        public final List<MemberChange> fields;

        private ClassDetail(String classType,
                            ValueChange<String> superclass,
                            List<InterfaceChange> interfaces,
                            ValueChange<String> classFileVersion,
                            List<String> oldModifiers,
                            List<String> newModifiers,
                            List<AnnotationDetail> annotations,
                            List<MemberChange> constructors,
                            List<MemberChange> methods,
                            List<MemberChange> fields) {
            this.classType = classType;
            this.superclass = superclass;
            this.interfaces = List.copyOf(interfaces);
            this.classFileVersion = classFileVersion;
            this.oldModifiers = List.copyOf(oldModifiers);
            this.newModifiers = List.copyOf(newModifiers);
            this.annotations = List.copyOf(annotations);
            this.constructors = List.copyOf(constructors);
            this.methods = List.copyOf(methods);
            this.fields = List.copyOf(fields);
        }
    }

    @SuppressWarnings("unused")
    public static final class InterfaceChange {
        public final String name;
        public final String changeStatus;
        public final boolean binaryCompatible;
        public final boolean sourceCompatible;
        public final List<CompatibilityChangeInfo> compatibilityChanges;

        private InterfaceChange(String name,
                                String changeStatus,
                                boolean binaryCompatible,
                                boolean sourceCompatible,
                                List<CompatibilityChangeInfo> compatibilityChanges) {
            this.name = name;
            this.changeStatus = changeStatus;
            this.binaryCompatible = binaryCompatible;
            this.sourceCompatible = sourceCompatible;
            this.compatibilityChanges = List.copyOf(compatibilityChanges);
        }
    }

    @SuppressWarnings("unused")
    public static final class MemberChange {
        public final String memberType;
        public final String name;
        public final String changeStatus;
        public final boolean binaryCompatible;
        public final boolean sourceCompatible;
        public final List<CompatibilityChangeInfo> compatibilityChanges;
        public final ValueChange<String> signature;
        public final ValueChange<String> value;
        public final List<String> oldModifiers;
        public final List<String> newModifiers;
        public final List<AnnotationDetail> annotations;
        public final List<String> parameterTypes;

        private MemberChange(String memberType,
                             String name,
                             String changeStatus,
                             boolean binaryCompatible,
                             boolean sourceCompatible,
                             List<CompatibilityChangeInfo> compatibilityChanges,
                             ValueChange<String> signature,
                             ValueChange<String> value,
                             List<String> oldModifiers,
                             List<String> newModifiers,
                             List<AnnotationDetail> annotations,
                             List<String> parameterTypes) {
            this.memberType = memberType;
            this.name = name;
            this.changeStatus = changeStatus;
            this.binaryCompatible = binaryCompatible;
            this.sourceCompatible = sourceCompatible;
            this.compatibilityChanges = List.copyOf(compatibilityChanges);
            this.signature = signature;
            this.value = value;
            this.oldModifiers = List.copyOf(oldModifiers);
            this.newModifiers = List.copyOf(newModifiers);
            this.annotations = List.copyOf(annotations);
            this.parameterTypes = List.copyOf(parameterTypes);
        }
    }

    @SuppressWarnings("unused")
    public static final class AnnotationDetail {
        public final String name;
        public final String changeStatus;
        public final boolean binaryCompatible;
        public final boolean sourceCompatible;
        public final List<CompatibilityChangeInfo> compatibilityChanges;
        public final List<AnnotationElementDetail> elements;

        private AnnotationDetail(String name,
                                 String changeStatus,
                                 boolean binaryCompatible,
                                 boolean sourceCompatible,
                                 List<CompatibilityChangeInfo> compatibilityChanges,
                                 List<AnnotationElementDetail> elements) {
            this.name = name;
            this.changeStatus = changeStatus;
            this.binaryCompatible = binaryCompatible;
            this.sourceCompatible = sourceCompatible;
            this.compatibilityChanges = List.copyOf(compatibilityChanges);
            this.elements = List.copyOf(elements);
        }
    }

    @SuppressWarnings("unused")
    public static final class AnnotationElementDetail {
        public final String name;
        public final String changeStatus;
        public final List<String> oldValues;
        public final List<String> newValues;
        public final List<CompatibilityChangeInfo> compatibilityChanges;

        private AnnotationElementDetail(String name,
                                        String changeStatus,
                                        List<String> oldValues,
                                        List<String> newValues,
                                        List<CompatibilityChangeInfo> compatibilityChanges) {
            this.name = name;
            this.changeStatus = changeStatus;
            this.oldValues = List.copyOf(oldValues);
            this.newValues = List.copyOf(newValues);
            this.compatibilityChanges = List.copyOf(compatibilityChanges);
        }
    }

    @SuppressWarnings("unused")
    public static final class CompatibilityChangeInfo {
        public final String type;
        public final boolean binaryCompatible;
        public final boolean sourceCompatible;
        public final String semanticVersionImpact;

        private CompatibilityChangeInfo(String type,
                                        boolean binaryCompatible,
                                        boolean sourceCompatible,
                                        String semanticVersionImpact) {
            this.type = type;
            this.binaryCompatible = binaryCompatible;
            this.sourceCompatible = sourceCompatible;
            this.semanticVersionImpact = semanticVersionImpact;
        }
    }

    @SuppressWarnings("unused")
    public static final class ValueChange<T> {
        public final T oldValue;
        public final T newValue;
        public final boolean changed;

        private ValueChange(T oldValue, T newValue, boolean changed) {
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.changed = changed;
        }

        static ValueChange<String> empty() {
            return new ValueChange<>(null, null, false);
        }
    }
}

