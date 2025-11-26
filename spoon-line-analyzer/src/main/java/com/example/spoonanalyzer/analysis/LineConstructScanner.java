package com.example.spoonanalyzer.analysis;

import com.example.spoonanalyzer.model.ConstructType;
import com.example.spoonanalyzer.model.ConstructUsage;
import com.example.spoonanalyzer.model.DependencyInfo;
import com.example.spoonanalyzer.resolution.DependencyResolver;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtArrayWrite;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.CtScanner;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class LineConstructScanner extends CtScanner {

    private final Path targetFile;
    private final int targetLine;
    private final DependencyResolver dependencyResolver;
    private final String codeLineContent;
    private final Set<ConstructUsage> usages = new LinkedHashSet<>();

    public LineConstructScanner(Path targetFile,
                                int targetLine,
                                DependencyResolver dependencyResolver,
                                String codeLineContent) {
        this.targetFile = targetFile.toAbsolutePath().normalize();
        this.targetLine = targetLine;
        this.dependencyResolver = dependencyResolver;
        this.codeLineContent = codeLineContent;
    }

    public Set<ConstructUsage> getUsages() {
        return usages;
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        if (isOnTargetLine(invocation)) {
            CtExecutableReference<T> executableReference = invocation.getExecutable();
            if (executableReference != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(executableReference);
                String fqn = executableReference.getDeclaringType() != null
                        ? executableReference.getDeclaringType().getQualifiedName()
                        : null;
                String memberFqn = buildMemberQualifiedName(fqn, executableReference.getSimpleName());
                usages.add(new ConstructUsage(
                        ConstructType.METHOD_INVOCATION,
                        ConstructDescriptors.describeExecutable(executableReference),
                        dependencyInfo,
                        invocation.getPosition(),
                        codeLineContent,
                        fqn,
                        memberFqn));
            }
        }
        super.visitCtInvocation(invocation);
    }

    @Override
    public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
        if (isOnTargetLine(ctConstructorCall)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(ctConstructorCall.getExecutable());
            String fqn = ctConstructorCall.getExecutable().getDeclaringType() != null
                    ? ctConstructorCall.getExecutable().getDeclaringType().getQualifiedName()
                    : null;
            String memberFqn = buildMemberQualifiedName(fqn, ctConstructorCall.getExecutable().getSimpleName());
            usages.add(new ConstructUsage(
                    ConstructType.CONSTRUCTOR_CALL,
                    ConstructDescriptors.describeExecutable(ctConstructorCall.getExecutable()),
                    dependencyInfo,
                    ctConstructorCall.getPosition(),
                    codeLineContent,
                    fqn,
                    memberFqn));
        }
        super.visitCtConstructorCall(ctConstructorCall);
    }

    @Override
    public <T> void visitCtNewClass(CtNewClass<T> ctNewClass) {
        if (isOnTargetLine(ctNewClass)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(ctNewClass.getExecutable());
            String fqn = ctNewClass.getExecutable().getDeclaringType() != null
                    ? ctNewClass.getExecutable().getDeclaringType().getQualifiedName()
                    : null;
            String memberFqn = buildMemberQualifiedName(fqn, ctNewClass.getExecutable().getSimpleName());
            usages.add(new ConstructUsage(
                    ConstructType.CONSTRUCTOR_CALL,
                    ConstructDescriptors.describeExecutable(ctNewClass.getExecutable()),
                    dependencyInfo,
                    ctNewClass.getPosition(),
                    codeLineContent,
                    fqn,
                    memberFqn));
        }
        super.visitCtNewClass(ctNewClass);
    }

    @Override
    public <T> void visitCtFieldRead(CtFieldRead<T> fieldRead) {
        handleFieldAccess(fieldRead, fieldRead.getVariable());
        super.visitCtFieldRead(fieldRead);
    }

    @Override
    public <T> void visitCtFieldWrite(CtFieldWrite<T> fieldWrite) {
        handleFieldAccess(fieldWrite, fieldWrite.getVariable());
        super.visitCtFieldWrite(fieldWrite);
    }

    @Override
    public <T> void visitCtTypeAccess(CtTypeAccess<T> typeAccess) {
        if (isOnTargetLine(typeAccess)) {
            CtTypeReference<T> typeReference = typeAccess.getAccessedType();
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(typeReference);
            String fqn = typeReference != null ? typeReference.getQualifiedName() : null;
            usages.add(new ConstructUsage(
                    ConstructType.TYPE_REFERENCE,
                    ConstructDescriptors.describeType(typeReference),
                    dependencyInfo,
                    typeAccess.getPosition(),
                    codeLineContent,
                    fqn));
        }
        super.visitCtTypeAccess(typeAccess);
    }

    @Override
    public <A extends java.lang.annotation.Annotation> void visitCtAnnotation(CtAnnotation<A> annotation) {
        if (isOnTargetLine(annotation)) {
            CtTypeReference<A> annotationType = annotation.getAnnotationType();
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(annotationType);
            String fqn = annotationType != null ? annotationType.getQualifiedName() : null;
            usages.add(new ConstructUsage(
                    ConstructType.ANNOTATION_USAGE,
                    ConstructDescriptors.describeType(annotationType),
                    dependencyInfo,
                    annotation.getPosition(),
                    codeLineContent,
                    fqn));
        }
        super.visitCtAnnotation(annotation);
    }

    @Override
    public <T, E extends spoon.reflect.code.CtExpression<?>> void visitCtExecutableReferenceExpression(
            CtExecutableReferenceExpression<T, E> expression) {
        if (isOnTargetLine(expression)) {
            CtExecutableReference<T> executableReference = expression.getExecutable();
            DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(executableReference);
            String fqn = executableReference.getDeclaringType() != null
                    ? executableReference.getDeclaringType().getQualifiedName()
                    : null;
            String memberFqn = buildMemberQualifiedName(fqn, executableReference.getSimpleName());
            usages.add(new ConstructUsage(
                    ConstructType.METHOD_INVOCATION,
                    ConstructDescriptors.describeExecutable(executableReference),
                    dependencyInfo,
                    expression.getPosition(),
                    codeLineContent,
                    fqn,
                    memberFqn));
        }
        super.visitCtExecutableReferenceExpression(expression);
    }

    @Override
    public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
        if (isOnTargetLine(reference)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(reference);
            String fqn = reference != null ? reference.getQualifiedName() : null;
            usages.add(new ConstructUsage(
                    ConstructType.TYPE_REFERENCE,
                    ConstructDescriptors.describeType(reference),
                    dependencyInfo,
                    reference.getPosition(),
                    codeLineContent,
                    fqn));
        }
        super.visitCtTypeReference(reference);
    }

    @Override
    public <T> void visitCtSuperAccess(CtSuperAccess<T> access) {
        if (isOnTargetLine(access)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(access.getType());
            String fqn = access.getType() != null ? access.getType().getQualifiedName() : null;
            usages.add(new ConstructUsage(
                    ConstructType.TYPE_REFERENCE,
                    fqn != null ? fqn : "<unknown type>",
                    dependencyInfo,
                    access.getPosition(),
                    codeLineContent,
                    fqn));
        }
        super.visitCtSuperAccess(access);
    }

    @Override
    public <T> void visitCtThisAccess(CtThisAccess<T> access) {
        if (isOnTargetLine(access)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(access.getType());
            String fqn = access.getType() != null ? access.getType().getQualifiedName() : null;
            usages.add(new ConstructUsage(
                    ConstructType.TYPE_REFERENCE,
                    fqn != null ? fqn : "<unknown type>",
                    dependencyInfo,
                    access.getPosition(),
                    codeLineContent,
                    fqn));
        }
        super.visitCtThisAccess(access);
    }

    private boolean isOnTargetLine(CtElement element) {
        if (element == null || element.getPosition() == null || !element.getPosition().isValidPosition()) {
            return false;
        }
        if (!targetFile.equals(element.getPosition().getFile().toPath().toAbsolutePath().normalize())) {
            return false;
        }
        int begin = element.getPosition().getLine();
        int end = element.getPosition().getEndLine();
        return targetLine >= begin && targetLine <= end;
    }

    @Override
    public <T> void visitCtArrayRead(CtArrayRead<T> arrayRead) {
        if (isOnTargetLine(arrayRead)) {
            CtTypeReference<?> arrayType = arrayRead.getType();
            if (arrayType != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(arrayType);
                String fqn = arrayType.getQualifiedName();
                usages.add(new ConstructUsage(
                        ConstructType.ARRAY_ACCESS,
                        ConstructDescriptors.describeType(arrayType),
                        dependencyInfo,
                        arrayRead.getPosition(),
                        codeLineContent,
                        fqn));
            }
        }
        super.visitCtArrayRead(arrayRead);
    }

    @Override
    public <T> void visitCtArrayWrite(CtArrayWrite<T> arrayWrite) {
        if (isOnTargetLine(arrayWrite)) {
            CtTypeReference<?> arrayType = arrayWrite.getType();
            if (arrayType != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(arrayType);
                String fqn = arrayType.getQualifiedName();
                usages.add(new ConstructUsage(
                        ConstructType.ARRAY_ACCESS,
                        ConstructDescriptors.describeType(arrayType),
                        dependencyInfo,
                        arrayWrite.getPosition(),
                        codeLineContent,
                        fqn));
            }
        }
        super.visitCtArrayWrite(arrayWrite);
    }

    @Override
    public <T> void visitCtLambda(CtLambda<T> lambda) {
        if (isOnTargetLine(lambda)) {
            CtTypeReference<?> functionalInterface = lambda.getType();
            if (functionalInterface != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(functionalInterface);
                String fqn = functionalInterface.getQualifiedName();
                usages.add(new ConstructUsage(
                        ConstructType.LAMBDA,
                        ConstructDescriptors.describeType(functionalInterface),
                        dependencyInfo,
                        lambda.getPosition(),
                        codeLineContent,
                        fqn));
            }
        }
        super.visitCtLambda(lambda);
    }

    // Type casts are captured through visitCtTypeReference when the type is used in a cast context
    // Enum values are captured through visitCtFieldRead when accessed as static fields
    // Variable access types are captured through visitCtTypeReference

    // Enum values are typically accessed as fields, so they're handled by visitCtFieldRead
    // Variable access types are captured through their type references in visitCtTypeReference

    @Override
    public void visitCtPackageReference(CtPackageReference packageReference) {
        if (isOnTargetLine(packageReference)) {
            String packageName = packageReference.getQualifiedName();
            DependencyInfo dependencyInfo = DependencyInfo.builder(
                    com.example.spoonanalyzer.model.DependencyOrigin.UNKNOWN).build();
            usages.add(new ConstructUsage(
                    ConstructType.PACKAGE_REFERENCE,
                    packageName,
                    dependencyInfo,
                    packageReference.getPosition(),
                    codeLineContent,
                    null));
        }
        super.visitCtPackageReference(packageReference);
    }

    @Override
    public <T> void visitCtArrayTypeReference(CtArrayTypeReference<T> arrayTypeReference) {
        if (isOnTargetLine(arrayTypeReference)) {
            CtTypeReference<?> componentType = arrayTypeReference.getComponentType();
            if (componentType != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(componentType);
                String fqn = componentType.getQualifiedName();
                usages.add(new ConstructUsage(
                        ConstructType.ARRAY_TYPE_REFERENCE,
                        ConstructDescriptors.describeType(componentType) + "[]",
                        dependencyInfo,
                        arrayTypeReference.getPosition(),
                        codeLineContent,
                        fqn));
            }
        }
        super.visitCtArrayTypeReference(arrayTypeReference);
    }

    @Override
    public void visitCtWildcardReference(CtWildcardReference wildcardReference) {
        if (isOnTargetLine(wildcardReference)) {
            CtTypeReference<?> boundingType = wildcardReference.getBoundingType();
            if (boundingType != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(boundingType);
                String fqn = boundingType.getQualifiedName();
                String signature = "?" + (wildcardReference.isUpper() ? " extends " : " super ") + fqn;
                usages.add(new ConstructUsage(
                        ConstructType.WILDCARD_REFERENCE,
                        signature,
                        dependencyInfo,
                        wildcardReference.getPosition(),
                        codeLineContent,
                        fqn));
            } else {
                DependencyInfo dependencyInfo = DependencyInfo.builder(
                        com.example.spoonanalyzer.model.DependencyOrigin.UNKNOWN).build();
                usages.add(new ConstructUsage(
                        ConstructType.WILDCARD_REFERENCE,
                        "?",
                        dependencyInfo,
                        wildcardReference.getPosition(),
                        codeLineContent,
                        null));
            }
        }
        super.visitCtWildcardReference(wildcardReference);
    }

    @Override
    public void visitCtTypeParameterReference(CtTypeParameterReference typeParameterReference) {
        if (isOnTargetLine(typeParameterReference)) {
            CtTypeReference<?> boundingType = typeParameterReference.getBoundingType();
            if (boundingType != null) {
                DependencyInfo dependencyInfo = dependencyResolver.resolveType(boundingType);
                String fqn = boundingType.getQualifiedName();
                usages.add(new ConstructUsage(
                        ConstructType.TYPE_PARAMETER_REFERENCE,
                        typeParameterReference.getSimpleName() + " extends " + fqn,
                        dependencyInfo,
                        typeParameterReference.getPosition(),
                        codeLineContent,
                        fqn));
            } else {
                DependencyInfo dependencyInfo = DependencyInfo.builder(
                        com.example.spoonanalyzer.model.DependencyOrigin.UNKNOWN).build();
                usages.add(new ConstructUsage(
                        ConstructType.TYPE_PARAMETER_REFERENCE,
                        typeParameterReference.getSimpleName(),
                        dependencyInfo,
                        typeParameterReference.getPosition(),
                        codeLineContent,
                        null));
            }
        }
        super.visitCtTypeParameterReference(typeParameterReference);
    }

    private <T> void handleFieldAccess(CtElement element, CtFieldReference<T> fieldReference) {
        if (!isOnTargetLine(element)) {
            return;
        }
        DependencyInfo dependencyInfo = dependencyResolver.resolveField(fieldReference);
        String fqn = fieldReference.getDeclaringType() != null
                ? fieldReference.getDeclaringType().getQualifiedName()
                : null;
        String memberFqn = buildMemberQualifiedName(fqn, fieldReference != null ? fieldReference.getSimpleName() : null);
        usages.add(new ConstructUsage(
                ConstructType.FIELD_ACCESS,
                ConstructDescriptors.describeField(fieldReference),
                dependencyInfo,
                element.getPosition(),
                codeLineContent,
                fqn,
                memberFqn));
    }

    private String buildMemberQualifiedName(String declaringTypeFqn, String memberName) {
        if (memberName == null || memberName.isBlank()) {
            return null;
        }
        if (declaringTypeFqn == null || declaringTypeFqn.isBlank()) {
            return memberName;
        }
        return declaringTypeFqn + "." + memberName;
    }
}

