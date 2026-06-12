package github.chains;

import spoon.Launcher;
import spoon.compiler.Environment;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        process("/workspace/lithium/src/main/java");
        process("/workspace/lithium/src/test/java");
    }

    private static void process(String sourceDir) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(sourceDir);
        
        Environment env = launcher.getEnvironment();
        env.setNoClasspath(true);
        env.setAutoImports(true);
        
        launcher.buildModel();

        // 2. Update type references
        for (CtTypeReference<?> ref : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qName = ref.getQualifiedName();
            if (qName == null) continue;

            if (qName.startsWith("javax.ws.rs.")) {
                replacePackage(launcher, ref, "javax.ws.rs", "jakarta.ws.rs");
            } else if (qName.startsWith("javax.servlet.")) {
                replacePackage(launcher, ref, "javax.servlet", "jakarta.servlet");
            } else if (qName.startsWith("javax.validation.")) {
                replacePackage(launcher, ref, "javax.validation", "jakarta.validation");
            } else if (qName.startsWith("javax.annotation.")) {
                replacePackage(launcher, ref, "javax.annotation", "jakarta.annotation");
            } else if (qName.equals("io.dropwizard.Application")) {
                replacePackage(launcher, ref, "io.dropwizard", "io.dropwizard.core");
            } else if (qName.equals("io.dropwizard.Configuration")) {
                replacePackage(launcher, ref, "io.dropwizard", "io.dropwizard.core");
            } else if (qName.startsWith("io.dropwizard.setup.")) {
                replacePackage(launcher, ref, "io.dropwizard.setup", "io.dropwizard.core.setup");
            } else if (qName.equals("io.dropwizard.logging.AbstractAppenderFactory")) {
                replacePackage(launcher, ref, "io.dropwizard.logging", "io.dropwizard.logging.common");
            } else if (qName.startsWith("io.dropwizard.logging.filter.")) {
                replacePackage(launcher, ref, "io.dropwizard.logging.filter", "io.dropwizard.logging.common.filter");
            } else if (qName.startsWith("io.dropwizard.logging.layout.")) {
                replacePackage(launcher, ref, "io.dropwizard.logging.layout", "io.dropwizard.logging.common.layout");
            } else if (qName.startsWith("io.dropwizard.logging.async.")) {
                replacePackage(launcher, ref, "io.dropwizard.logging.async", "io.dropwizard.logging.common.async");
            } else if (qName.equals("com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider")) {
                replacePackage(launcher, ref, "com.fasterxml.jackson.jaxrs.json", "com.fasterxml.jackson.jakarta.rs.json");
            } else if (qName.equals("io.dropwizard.util.Strings")) {
                replacePackage(launcher, ref, "io.dropwizard.util", "com.google.common.base");
            }
        }

        // 1. Update imports
        for (CtImport ctImport : launcher.getModel().getElements(new TypeFilter<>(CtImport.class))) {
            CtReference ref = ctImport.getReference();
            if (ref != null) {
                String qName = ref.toString();
                
                if (qName.startsWith("javax.ws.rs.")) {
                    updateImport(launcher, ctImport, "javax.ws.rs", "jakarta.ws.rs");
                } else if (qName.startsWith("javax.servlet.")) {
                    updateImport(launcher, ctImport, "javax.servlet", "jakarta.servlet");
                } else if (qName.startsWith("javax.validation.")) {
                    updateImport(launcher, ctImport, "javax.validation", "jakarta.validation");
                } else if (qName.startsWith("javax.annotation.")) {
                    updateImport(launcher, ctImport, "javax.annotation", "jakarta.annotation");
                } else if (qName.startsWith("io.dropwizard.Application")) {
                    updateImport(launcher, ctImport, "io.dropwizard.Application", "io.dropwizard.core.Application");
                } else if (qName.startsWith("io.dropwizard.Configuration")) {
                    updateImport(launcher, ctImport, "io.dropwizard.Configuration", "io.dropwizard.core.Configuration");
                } else if (qName.startsWith("io.dropwizard.setup.")) {
                    updateImport(launcher, ctImport, "io.dropwizard.setup", "io.dropwizard.core.setup");
                } else if (qName.startsWith("io.dropwizard.logging.AbstractAppenderFactory")) {
                    updateImport(launcher, ctImport, "io.dropwizard.logging", "io.dropwizard.logging.common");
                } else if (qName.startsWith("io.dropwizard.logging.filter.")) {
                    updateImport(launcher, ctImport, "io.dropwizard.logging.filter", "io.dropwizard.logging.common.filter");
                } else if (qName.startsWith("io.dropwizard.logging.layout.")) {
                    updateImport(launcher, ctImport, "io.dropwizard.logging.layout", "io.dropwizard.logging.common.layout");
                } else if (qName.startsWith("io.dropwizard.logging.async.")) {
                    updateImport(launcher, ctImport, "io.dropwizard.logging.async", "io.dropwizard.logging.common.async");
                } else if (qName.equals("com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider")) {
                    updateImport(launcher, ctImport, "com.fasterxml.jackson.jaxrs.json", "com.fasterxml.jackson.jakarta.rs.json");
                } else if (qName.equals("io.dropwizard.util.Strings")) {
                    updateImport(launcher, ctImport, "io.dropwizard.util", "com.google.common.base");
                }
            }
        }


        launcher.setSourceOutputDirectory(sourceDir);
        launcher.prettyprint();
    }

    private static void updateImport(Launcher launcher, CtImport ctImport, String oldPrefix, String newPrefix) {
        String oldName = ctImport.getReference().toString();
        String newName = newPrefix + oldName.substring(oldPrefix.length());
        CtReference newRef = launcher.getFactory().Type().createReference(newName);
        ctImport.setReference(newRef);
    }

    private static void replacePackage(Launcher launcher, CtTypeReference<?> ref, String oldPrefix, String newPrefix) {
        if (ref.getPackage() != null) {
            String currentPkg = ref.getPackage().getQualifiedName();
            if (currentPkg.startsWith(oldPrefix)) {
                String newPkgName = newPrefix + currentPkg.substring(oldPrefix.length());
                CtPackageReference newPkg = launcher.getFactory().Package().getOrCreate(newPkgName).getReference();
                ref.setPackage(newPkg);
            }
        }
    }
}