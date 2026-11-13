package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Main {

    private static final String LEGACY_PASSWORD_ENCODER = "org.jasypt.spring.security.PasswordEncoder";
    private static final String LEGACY_PBE_PASSWORD_ENCODER = "org.jasypt.spring.security.PBEPasswordEncoder";
    private static final Pattern PASSWORD_ENCODER_IMPORT = Pattern.compile("^\\s*import\\s+org\\.jasypt\\.spring\\.security\\.PasswordEncoder;\\s*$", Pattern.MULTILINE);
    private static final Pattern PBE_PASSWORD_ENCODER_IMPORT = Pattern.compile("^\\s*import\\s+org\\.jasypt\\.spring\\.security\\.PBEPasswordEncoder;\\s*$", Pattern.MULTILINE);

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected an input source directory");
        }

        Path inputRoot = Paths.get(args[0]).toAbsolutePath().normalize();

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(inputRoot.toString());
        CtModel model = launcher.buildModel();

        for (Path javaFile : findAffectedJavaFiles(model)) {
            rewriteLegacyJasyptWrappers(javaFile);
        }
    }

    private static Set<Path> findAffectedJavaFiles(CtModel model) {
        Set<Path> files = new LinkedHashSet<>();
        for (CtTypeReference<?> reference : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
            String qn = reference.getQualifiedName();
            if (LEGACY_PASSWORD_ENCODER.equals(qn) || LEGACY_PBE_PASSWORD_ENCODER.equals(qn)) {
                if (reference.getPosition() != null && reference.getPosition().getFile() != null) {
                    files.add(reference.getPosition().getFile().toPath().toAbsolutePath().normalize());
                }
            }
        }
        return files;
    }

    private static Set<Path> findMavenSourceRoots(Path inputRoot) throws IOException {
        Set<Path> roots = new LinkedHashSet<>();
        try (var stream = Files.walk(inputRoot)) {
            stream.filter(path -> path.endsWith(Paths.get("src", "main", "java")))
                    .forEach(roots::add);
        }
        return roots;
    }

    private static void rewriteLegacyJasyptWrappers(Path file) throws IOException {
        if (!Files.exists(file) || !file.toString().endsWith(".java")) {
            return;
        }

        String original = Files.readString(file, StandardCharsets.UTF_8);
        String updated = original
                .replace("import org.jasypt.spring.security.PasswordEncoder;\n", "")
                .replace("import org.jasypt.spring.security.PBEPasswordEncoder;\n", "");

        updated = PASSWORD_ENCODER_IMPORT.matcher(updated).replaceAll("");
        updated = PBE_PASSWORD_ENCODER_IMPORT.matcher(updated).replaceAll("");

        updated = updated.replace(
                "PasswordEncoder encoder = new PasswordEncoder();\n\t\t        encoder.setPasswordEncryptor(new StrongPasswordEncryptor());",
                "org.acegisecurity.providers.encoding.PasswordEncoder encoder = new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                        "                private final org.jasypt.util.password.PasswordEncryptor passwordEncryptor = new org.jasypt.util.password.StrongPasswordEncryptor();\n" +
                        "\n" +
                        "                @Override\n" +
                        "                public String encodePassword(String rawPass, Object salt) {\n" +
                        "                    return passwordEncryptor.encryptPassword(rawPass);\n" +
                        "                }\n" +
                        "\n" +
                        "                @Override\n" +
                        "                public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
                        "                    return passwordEncryptor.checkPassword(rawPass, encPass);\n" +
                        "                }\n" +
                        "            };"
        );

        updated = updated.replace(
                "PBEPasswordEncoder encoder = new PBEPasswordEncoder();\n\t\t\tencoder.setPbeStringEncryptor(stringEncrypter);",
                "org.acegisecurity.providers.encoding.PasswordEncoder encoder = new org.acegisecurity.providers.encoding.PasswordEncoder() {\n" +
                        "            private final org.jasypt.encryption.pbe.PBEStringEncryptor pbeStringEncryptor = stringEncrypter;\n" +
                        "\n" +
                        "            @Override\n" +
                        "            public String encodePassword(String rawPass, Object salt) {\n" +
                        "                return pbeStringEncryptor.encrypt(rawPass);\n" +
                        "            }\n" +
                        "\n" +
                        "            @Override\n" +
                        "            public boolean isPasswordValid(String encPass, String rawPass, Object salt) {\n" +
                        "                try {\n" +
                        "                    return rawPass.equals(pbeStringEncryptor.decrypt(encPass));\n" +
                        "                } catch (RuntimeException ex) {\n" +
                        "                    return false;\n" +
                        "                }\n" +
                        "            }\n" +
                        "        };"
        );

        updated = updated.replace(
                "\t\t\tencoder.setPbeStringEncryptor(stringEncrypter);\n",
                "");
        updated = updated.replace(
                "\t\t        encoder.setPasswordEncryptor(new StrongPasswordEncryptor());\n",
                "");

        if (!updated.equals(original)) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        }
    }
}
