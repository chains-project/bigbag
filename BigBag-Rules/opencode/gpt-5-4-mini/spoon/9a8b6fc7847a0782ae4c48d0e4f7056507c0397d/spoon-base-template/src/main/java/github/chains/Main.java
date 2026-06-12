package github.chains;

import spoon.Launcher;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class Main {
    private static final String WRAPPER_PACKAGE = "org.jasypt.spring.security";
    private static final String PASSWORD_ENCODER = WRAPPER_PACKAGE + ".PasswordEncoder";
    private static final String PBE_PASSWORD_ENCODER = WRAPPER_PACKAGE + ".PBEPasswordEncoder";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: <source-dir> [output-dir]");
        }

        Path inputDir = Path.of(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1 ? Path.of(args[1]).toAbsolutePath().normalize() : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());
        launcher.buildModel();
        launcher.prettyprint();

        if (usesRemovedWrappers(launcher)) {
            writeCompatibilityShim(outputDir, WRAPPER_PACKAGE, "PasswordEncoder", passwordEncoderSource());
            writeCompatibilityShim(outputDir, WRAPPER_PACKAGE, "PBEPasswordEncoder", pbePasswordEncoderSource());
        }
    }

    private static boolean usesRemovedWrappers(Launcher launcher) {
        Set<String> references = new HashSet<>();
        for (CtTypeReference<?> reference : launcher.getModel().getElements(new TypeFilter<>(CtTypeReference.class))) {
            references.add(reference.getQualifiedName());
            if (references.contains(PASSWORD_ENCODER) || references.contains(PBE_PASSWORD_ENCODER)) {
                return true;
            }
        }
        return false;
    }

    private static void writeCompatibilityShim(Path outputDir, String packageName, String simpleName, String source) throws IOException {
        Path packageDir = outputDir.resolve(packageName.replace('.', '/'));
        Files.createDirectories(packageDir);
        Files.writeString(packageDir.resolve(simpleName + ".java"), source, StandardCharsets.UTF_8);
    }

    private static String passwordEncoderSource() {
        return "package org.jasypt.spring.security;\n\n"
                + "public class PasswordEncoder implements org.acegisecurity.providers.encoding.PasswordEncoder {\n"
                + "    private org.jasypt.util.password.PasswordEncryptor passwordEncryptor;\n\n"
                + "    public void setPasswordEncryptor(org.jasypt.util.password.PasswordEncryptor passwordEncryptor) {\n"
                + "        this.passwordEncryptor = passwordEncryptor;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String encodePassword(String rawPass, Object salt) {\n"
                + "        return passwordEncryptor.encryptPassword(rawPass);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n"
                + "        return passwordEncryptor.checkPassword(rawPass, encPass);\n"
                + "    }\n"
                + "}\n";
    }

    private static String pbePasswordEncoderSource() {
        return "package org.jasypt.spring.security;\n\n"
                + "public class PBEPasswordEncoder implements org.acegisecurity.providers.encoding.PasswordEncoder {\n"
                + "    private org.jasypt.encryption.pbe.PBEStringEncryptor pbeStringEncryptor;\n\n"
                + "    public void setPbeStringEncryptor(org.jasypt.encryption.pbe.PBEStringEncryptor pbeStringEncryptor) {\n"
                + "        this.pbeStringEncryptor = pbeStringEncryptor;\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public String encodePassword(String rawPass, Object salt) {\n"
                + "        return pbeStringEncryptor.encrypt(rawPass);\n"
                + "    }\n\n"
                + "    @Override\n"
                + "    public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n"
                + "        return rawPass.equals(pbeStringEncryptor.decrypt(encPass));\n"
                + "    }\n"
                + "}\n";
    }
}
