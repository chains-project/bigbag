package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

public class Main {

    private static final String OLD_SPRING_PASSWORD_ENCODER = "org.jasypt.spring.security.PasswordEncoder";
    private static final String OLD_SPRING_PBE_PASSWORD_ENCODER = "org.jasypt.spring.security.PBEPasswordEncoder";
    private static final String NEW_ACEGI_PASSWORD_ENCODER = "org.acegisecurity.providers.encoding.PasswordEncoder";
    private static final String NEW_JASYPT_PASSWORD_ENCRYPTOR = "org.jasypt.util.password.PasswordEncryptor";
    private static final String NEW_STRONG_PASSWORD_ENCRYPTOR = "org.jasypt.util.password.StrongPasswordEncryptor";
    private static final String NEW_PBE_STRING_ENCRYPTOR = "org.jasypt.encryption.pbe.PBEStringEncryptor";
    private static final String NEW_STANDARD_PBE_STRING_ENCRYPTOR = "org.jasypt.encryption.pbe.StandardPBEStringEncryptor";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <source-dir> [output-dir]");
        }

        Path inputDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputDir = args.length > 1
                ? Paths.get(args[1]).toAbsolutePath().normalize()
                : inputDir;

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.addInputResource(inputDir.toString());
        launcher.setSourceOutputDirectory(outputDir.toFile());

        launcher.buildModel();
        transform(launcher.getModel());
        launcher.prettyprint();

        removeDeprecatedImports(outputDir);
    }

    private static void transform(spoon.reflect.CtModel model) {
        for (CtType<?> type : model.getAllTypes()) {
            for (CtBlock<?> block : type.getElements(new TypeFilter<>(CtBlock.class))) {
                transformBlock(block);
            }
        }
    }

    private static void transformBlock(CtBlock<?> block) {
        List<CtStatement> statements = block.getStatements();
        for (int i = 0; i < statements.size(); i++) {
            CtStatement statement = statements.get(i);
            if (!(statement instanceof CtLocalVariable<?> localVariable)) {
                continue;
            }

            CtExpression<?> defaultExpression = localVariable.getDefaultExpression();
            if (!(defaultExpression instanceof CtConstructorCall<?> constructorCall)) {
                continue;
            }

            CtTypeReference<?> constructedType = constructorCall.getType();
            if (constructedType == null) {
                continue;
            }

            String constructedTypeName = constructedType.getQualifiedName();
            if (!OLD_SPRING_PASSWORD_ENCODER.equals(constructedTypeName)
                    && !OLD_SPRING_PBE_PASSWORD_ENCODER.equals(constructedTypeName)) {
                continue;
            }

            if (i + 1 >= statements.size()) {
                continue;
            }

            CtStatement nextStatement = statements.get(i + 1);
            if (!(nextStatement instanceof CtInvocation<?> invocation)) {
                continue;
            }

            if (!isInvocationOnLocalVariable(invocation, localVariable)) {
                continue;
            }

            String methodName = invocation.getExecutable() == null ? null : invocation.getExecutable().getSimpleName();
            if (OLD_SPRING_PASSWORD_ENCODER.equals(constructedTypeName)
                    && "setPasswordEncryptor".equals(methodName)
                    && invocation.getArguments().size() == 1) {
                String delegateExpression = invocation.getArguments().get(0).toString();
                localVariable.setDefaultExpression((CtExpression) createSnippet(buildStrongPasswordAdapter(delegateExpression)));
                statements.remove(i + 1);
            } else if (OLD_SPRING_PBE_PASSWORD_ENCODER.equals(constructedTypeName)
                    && "setPbeStringEncryptor".equals(methodName)
                    && invocation.getArguments().size() == 1) {
                String delegateExpression = invocation.getArguments().get(0).toString();
                localVariable.setDefaultExpression((CtExpression) createSnippet(buildPbePasswordAdapter(delegateExpression)));
                statements.remove(i + 1);
            }
        }
    }

    private static boolean isInvocationOnLocalVariable(CtInvocation<?> invocation, CtLocalVariable<?> localVariable) {
        CtExpression<?> target = invocation.getTarget();
        return target != null && Objects.equals(target.toString(), localVariable.getSimpleName());
    }

    private static CtExpression<?> createSnippet(String code) {
        return new Launcher().getFactory().Code().createCodeSnippetExpression(code);
    }

    private static String buildStrongPasswordAdapter(String delegateExpression) {
        return "new " + NEW_ACEGI_PASSWORD_ENCODER + "() {"
                + "private final " + NEW_JASYPT_PASSWORD_ENCRYPTOR + " delegate = " + delegateExpression + ";"
                + "@Override public String encodePassword(String rawPass, Object salt) { return delegate.encryptPassword(rawPass); }"
                + "@Override public boolean isPasswordValid(String encPass, String rawPass, Object salt) { return delegate.checkPassword(rawPass, encPass); }"
                + "}";
    }

    private static String buildPbePasswordAdapter(String delegateExpression) {
        return "new " + NEW_ACEGI_PASSWORD_ENCODER + "() {"
                + "private final " + NEW_PBE_STRING_ENCRYPTOR + " delegate = " + delegateExpression + ";"
                + "@Override public String encodePassword(String rawPass, Object salt) { return delegate.encrypt(rawPass); }"
                + "@Override public boolean isPasswordValid(String encPass, String rawPass, Object salt) { return rawPass != null && rawPass.equals(delegate.decrypt(encPass)); }"
                + "}";
    }

    private static void removeDeprecatedImports(Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) {
            return;
        }

        try (var paths = Files.walk(outputDir)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(Main::stripDeprecatedImports);
        }
    }

    private static void stripDeprecatedImports(Path file) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean changed = false;
            StringBuilder builder = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("import org.jasypt.spring.security.")) {
                    changed = true;
                    continue;
                }
                builder.append(line).append(System.lineSeparator());
            }
            if (changed) {
                Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }
}
