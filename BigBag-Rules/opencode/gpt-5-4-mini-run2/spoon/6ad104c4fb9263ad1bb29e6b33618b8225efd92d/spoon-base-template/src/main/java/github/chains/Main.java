package github.chains;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtThrow;
import spoon.reflect.reference.CtTypeReference;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <input-source-dir> [output-dir]");
        }

        Path input = Paths.get(args[0]);
        Path output = args.length >= 2 ? Paths.get(args[1]) : input.resolveSibling(input.getFileName() + "-fixed");

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.addInputResource(input.toString());
        launcher.setSourceOutputDirectory(output.toFile());

        launcher.buildModel();
        launcher.addProcessor(new TlsFatalAlertRewriteProcessor());
        launcher.process();
        launcher.prettyprint();
    }

    private static class TlsFatalAlertRewriteProcessor extends AbstractProcessor<CtConstructorCall<?>> {
        @Override
        public void process(CtConstructorCall<?> call) {
            CtTypeReference<?> type = call.getExecutable() == null ? null : call.getExecutable().getDeclaringType();
            if (type == null || !"org.bouncycastle.crypto.tls.TlsFatalAlert".equals(type.getQualifiedName())) {
                return;
            }

            CtThrow parentThrow = call.getParent(CtThrow.class);
            if (parentThrow == null) {
                return;
            }

            String message = extractMessage(call);
            String replacement =
                    "new org.bouncycastle.crypto.io.InvalidCipherTextIOException("
                            + quote(message)
                            + ", new org.bouncycastle.crypto.InvalidCipherTextException("
                            + quote(message)
                            + "))";
            parentThrow.setThrownExpression(getFactory().Code().createCodeSnippetExpression(replacement));
        }

        private String extractMessage(CtConstructorCall<?> call) {
            if (call.getArguments().size() != 1 || !(call.getArguments().get(0) instanceof CtFieldRead)) {
                return "invalid_cipher_text";
            }

            CtFieldRead<?> fieldRead = (CtFieldRead<?>) call.getArguments().get(0);
            if (fieldRead.getVariable() == null || fieldRead.getVariable().getDeclaringType() == null) {
                return "invalid_cipher_text";
            }

            String owner = fieldRead.getVariable().getDeclaringType().getQualifiedName();
            if (!"org.bouncycastle.crypto.tls.AlertDescription".equals(owner)) {
                return "invalid_cipher_text";
            }

            return fieldRead.getVariable().getSimpleName();
        }

        private String quote(String text) {
            return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }
}
