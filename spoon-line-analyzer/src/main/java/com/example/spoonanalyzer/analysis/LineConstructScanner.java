package com.example.spoonanalyzer.analysis;

import com.example.spoonanalyzer.model.ConstructType;
import com.example.spoonanalyzer.model.ConstructUsage;
import com.example.spoonanalyzer.model.DependencyInfo;
import com.example.spoonanalyzer.resolution.DependencyResolver;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public class LineConstructScanner extends CtScanner {

    private final Path targetFile;
    private final int targetLine;
    private final DependencyResolver dependencyResolver;
    private final Set<ConstructUsage> usages = new LinkedHashSet<>();

    public LineConstructScanner(Path targetFile, int targetLine, DependencyResolver dependencyResolver) {
        this.targetFile = targetFile.toAbsolutePath().normalize();
        this.targetLine = targetLine;
        this.dependencyResolver = dependencyResolver;
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
                usages.add(new ConstructUsage(
                        ConstructType.METHOD_INVOCATION,
                        ConstructDescriptors.describeExecutable(executableReference),
                        dependencyInfo,
                        invocation.getPosition()));
            }
        }
        super.visitCtInvocation(invocation);
    }

    @Override
    public <T> void visitCtConstructorCall(CtConstructorCall<T> ctConstructorCall) {
        if (isOnTargetLine(ctConstructorCall)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(ctConstructorCall.getExecutable());
            usages.add(new ConstructUsage(
                    ConstructType.CONSTRUCTOR_CALL,
                    ConstructDescriptors.describeExecutable(ctConstructorCall.getExecutable()),
                    dependencyInfo,
                    ctConstructorCall.getPosition()));
        }
        super.visitCtConstructorCall(ctConstructorCall);
    }

    @Override
    public <T> void visitCtNewClass(CtNewClass<T> ctNewClass) {
        if (isOnTargetLine(ctNewClass)) {
            DependencyInfo dependencyInfo = dependencyResolver.resolveExecutable(ctNewClass.getExecutable());
            usages.add(new ConstructUsage(
                    ConstructType.CONSTRUCTOR_CALL,
                    ConstructDescriptors.describeExecutable(ctNewClass.getExecutable()),
                    dependencyInfo,
                    ctNewClass.getPosition()));
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
            usages.add(new ConstructUsage(
                    ConstructType.TYPE_REFERENCE,
                    ConstructDescriptors.describeType(typeReference),
                    dependencyInfo,
                    typeAccess.getPosition()));
        }
        super.visitCtTypeAccess(typeAccess);
    }

    @Override
    public <A extends java.lang.annotation.Annotation> void visitCtAnnotation(CtAnnotation<A> annotation) {
        if (isOnTargetLine(annotation)) {
            CtTypeReference<A> annotationType = annotation.getAnnotationType();
            DependencyInfo dependencyInfo = dependencyResolver.resolveType(annotationType);
            usages.add(new ConstructUsage(
                    ConstructType.ANNOTATION_USAGE,
                    ConstructDescriptors.describeType(annotationType),
                    dependencyInfo,
                    annotation.getPosition()));
        }
        super.visitCtAnnotation(annotation);
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

    private <T> void handleFieldAccess(CtElement element, CtFieldReference<T> fieldReference) {
        if (!isOnTargetLine(element)) {
            return;
        }
        DependencyInfo dependencyInfo = dependencyResolver.resolveField(fieldReference);
        usages.add(new ConstructUsage(
                ConstructType.FIELD_ACCESS,
                ConstructDescriptors.describeField(fieldReference),
                dependencyInfo,
                element.getPosition()));
    }
}

