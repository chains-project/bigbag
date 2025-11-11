package com.example.japicmp;

import com.example.japicmp.model.AnnotationDetail;
import com.example.japicmp.model.AnnotationElementDetail;
import com.example.japicmp.model.ClassChange;
import com.example.japicmp.model.ClassDetail;
import com.example.japicmp.model.ComparisonReport;
import com.example.japicmp.model.CompatibilityChangeInfo;
import com.example.japicmp.model.InterfaceChange;
import com.example.japicmp.model.MemberChange;
import com.example.japicmp.model.Summary;
import com.example.japicmp.model.ValueChange;
import japicmp.cmp.JApiCmpArchive;
import japicmp.cmp.JarArchiveComparator;
import japicmp.cmp.JarArchiveComparatorOptions;
import japicmp.config.Options;
import japicmp.model.AccessModifier;
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

final class JapicmpReportGenerator {

    private JapicmpReportGenerator() {
    }

    static ComparisonReport generate(Path oldJar, Path newJar) {
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
                .map(JapicmpReportGenerator::mapClassChange)
                .collect(Collectors.toList());

        long changedMembers = allClasses.stream()
                .mapToLong(JapicmpReportGenerator::countChangedMembers)
                .sum();

        long changedClassesCount = allClasses.stream()
                .filter(JapicmpReportGenerator::hasRelevantChange)
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
        long methods = jApiClass.getMethods().stream().filter(JapicmpReportGenerator::hasBehaviorChange).count();
        long constructors = jApiClass.getConstructors().stream().filter(JapicmpReportGenerator::hasBehaviorChange).count();
        long fields = jApiClass.getFields().stream().filter(JapicmpReportGenerator::hasFieldChange).count();
        return methods + constructors + fields;
    }

    private static boolean hasRelevantChange(JApiClass jApiClass) {
        boolean memberChanges = jApiClass.getMethods().stream().anyMatch(JapicmpReportGenerator::hasBehaviorChange)
                || jApiClass.getConstructors().stream().anyMatch(JapicmpReportGenerator::hasBehaviorChange)
                || jApiClass.getFields().stream().anyMatch(JapicmpReportGenerator::hasFieldChange);

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
                .sorted(Comparator.comparing(InterfaceChange::name))
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
                        ValueChange.<String>empty(),
                        collectModifierStrings(constructor.getModifiers(), false),
                        collectModifierStrings(constructor.getModifiers(), true),
                        mapAnnotations(constructor.getAnnotations()),
                        constructor.getParameters().stream().map(JApiParameter::getType).collect(Collectors.toList())
                ))
                .sorted(Comparator.comparing(MemberChange::name))
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
                .sorted(Comparator.comparing(MemberChange::name))
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
                .sorted(Comparator.comparing(MemberChange::name))
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
                .sorted(Comparator.comparing(AnnotationDetail::name))
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
                .sorted(Comparator.comparing(AnnotationElementDetail::name))
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
}

