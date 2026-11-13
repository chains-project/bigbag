package github.chains;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import spoon.Launcher;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final Map<String, String> TYPE_REWRITES = new LinkedHashMap<>();

    static {
        TYPE_REWRITES.put("org.junit.Assert", "org.junit.jupiter.api.Assertions");
        TYPE_REWRITES.put("org.junit.Assume", "org.junit.jupiter.api.Assumptions");
        TYPE_REWRITES.put("org.junit.Ignore", "org.junit.jupiter.api.Disabled");
        TYPE_REWRITES.put("org.junit.Test", "org.junit.jupiter.api.Test");
        TYPE_REWRITES.put("org.junit.Before", "org.junit.jupiter.api.BeforeEach");
        TYPE_REWRITES.put("org.junit.After", "org.junit.jupiter.api.AfterEach");
        TYPE_REWRITES.put("org.junit.BeforeClass", "org.junit.jupiter.api.BeforeAll");
        TYPE_REWRITES.put("org.junit.AfterClass", "org.junit.jupiter.api.AfterAll");
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-source-dir]");
        }

        File inputDir = new File(args[0]);
        File outputDir = args.length >= 2 ? new File(args[1]) : inputDir;

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir.getAbsolutePath());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setSourceOutputDirectory(outputDir);
        launcher.buildModel();

        rewriteTypes(launcher);
        rewriteExecutables(launcher);
        rewriteAnnotations(launcher);
        rewriteImports(launcher);

        launcher.prettyprint();
    }

    private static void rewriteTypes(Launcher launcher) {
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String replacement = TYPE_REWRITES.get(ref.getQualifiedName());
            if (replacement != null) {
                ref.replace(launcher.getFactory().Type().createReference(replacement));
            }
        }
    }

    private static void rewriteExecutables(Launcher launcher) {
        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            CtTypeReference<?> declaringType = executable.getDeclaringType();
            if (declaringType == null) {
                continue;
            }

            String replacement = TYPE_REWRITES.get(declaringType.getQualifiedName());
            if (replacement != null) {
                executable.setDeclaringType(launcher.getFactory().Type().createReference(replacement));
            }
        }
    }

    private static void rewriteAnnotations(Launcher launcher) {
        for (CtAnnotation<?> annotation : launcher.getModel().getElements(new TypeFilter<>(CtAnnotation.class))) {
            CtTypeReference<?> type = annotation.getAnnotationType();
            if (type == null) {
                continue;
            }

            String replacement = TYPE_REWRITES.get(type.getQualifiedName());
            if (replacement != null) {
                annotation.setAnnotationType(launcher.getFactory().Type().createReference(replacement));
            }
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void rewriteImports(Launcher launcher) {
        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            if (ctImport.getReference() == null) {
                continue;
            }

            if (ctImport.getReference() instanceof CtTypeReference) {
                CtTypeReference<?> typeRef = (CtTypeReference<?>) ctImport.getReference();
                String replacement = TYPE_REWRITES.get(typeRef.getQualifiedName());
                if (replacement != null) {
                    ctImport.setReference(launcher.getFactory().Type().createReference(replacement));
                }
            } else if (ctImport.getReference() instanceof CtExecutableReference) {
                CtExecutableReference<?> execRef = (CtExecutableReference<?>) ctImport.getReference();
                CtTypeReference<?> declaringType = execRef.getDeclaringType();
                if (declaringType != null) {
                    String replacement = TYPE_REWRITES.get(declaringType.getQualifiedName());
                    if (replacement != null) {
                        execRef.setDeclaringType(launcher.getFactory().Type().createReference(replacement));
                    }
                }
            }
        }
    }

}
