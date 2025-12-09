package org.example.migration;

import spoon.Launcher;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.sniper.SniperJavaPrettyPrinter;

import java.util.List;

public class FopRefactoring {

    public static class FopApiProcessor extends AbstractProcessor<CtInvocation<?>> {

        @Override
        public boolean isToBeProcessed(CtInvocation<?> candidate) {
            String methodName = candidate.getExecutable().getSimpleName();
            if (!"newInstance".equals(methodName)) {
                return false;
            }

            CtTypeReference<?> declaringType = candidate.getExecutable().getDeclaringType();
            if (declaringType == null) {
                return false;
            }
            String ownerName = declaringType.getQualifiedName();

            // Case 1: FopFactory.newInstance() (0 args)
            if (ownerName.contains("FopFactory") && candidate.getArguments().isEmpty()) {
                return true;
            }

            // Case 2: PDFEncryptionManager.newInstance(int, params) (2 args)
            if (ownerName.contains("PDFEncryptionManager") && candidate.getArguments().size() == 2) {
                CtExpression<?> firstArg = candidate.getArguments().get(0);
                CtTypeReference<?> firstArgType = firstArg.getType();
                
                // Defensive: Check if first arg is likely an int (primitive or literal)
                // In NoClasspath, getType() might be null, so we also check if it's a literal integer
                boolean isIntType = (firstArgType != null && "int".equals(firstArgType.getSimpleName()));
                boolean isIntLiteral = (firstArg instanceof CtLiteral && ((CtLiteral<?>) firstArg).getValue() instanceof Integer);
                
                return isIntType || isIntLiteral;
            }

            return false;
        }

        @Override
        public void process(CtInvocation<?> invocation) {
            Factory factory = getFactory();
            String ownerName = invocation.getExecutable().getDeclaringType().getQualifiedName();

            // --- REFACTORING STRATEGY 1: FopFactory ---
            // Old: FopFactory.newInstance()
            // New: FopFactory.newInstance(new java.io.File(".").toURI())
            if (ownerName.contains("FopFactory")) {
                
                // 1. Create 'new java.io.File(".")'
                CtTypeReference<java.io.File> fileType = factory.Type().createReference("java.io.File");
                CtConstructorCall<java.io.File> newFile = factory.Code().createConstructorCall(
                        fileType, 
                        factory.Code().createLiteral(".")
                );

                // 2. Create '.toURI()' invocation on the file object
                CtInvocation<?> toUriCall = factory.Code().createInvocation(
                        newFile,
                        factory.Method().createReference(fileType, factory.Type().createReference("java.net.URI"), "toURI")
                );

                // 3. Inject argument
                invocation.addArgument(toUriCall);
                
                System.out.println("Refactored FopFactory.newInstance() at line " + invocation.getPosition().getLine());
            } 
            
            // --- REFACTORING STRATEGY 2: PDFEncryptionManager ---
            // Old: newInstance(int keyLen, PDFEncryptionParams params)
            // New: newInstance(PDFEncryptionParams params, PDFDocument doc)
            else if (ownerName.contains("PDFEncryptionManager")) {
                List<CtExpression<?>> args = invocation.getArguments();
                
                // Keep the params argument (originally 2nd, index 1)
                CtExpression<?> paramsArg = args.get(1).clone();
                
                // Create a placeholder for the required PDFDocument (originally unknown in this context)
                CtExpression<?> nullDoc = factory.Code().createLiteral(null);
                nullDoc.addComment(factory.Code().createInlineComment("TODO: Pass valid PDFDocument instance here"));

                // Clear and Rebuild arguments: (params, null)
                invocation.getArguments().clear();
                invocation.addArgument(paramsArg);
                invocation.addArgument(nullDoc);

                System.out.println("Refactored PDFEncryptionManager.newInstance(...) at line " + invocation.getPosition().getLine());
            }
        }
    }

    public static void main(String[] args) {
        // Default paths (editable by user)
        String inputPath = "/home/kth/Documents/last_transformer/output/36859167815292f279e570d39dd2ddbcf1622dc6/billy/billy-gin/src/main/java/com/premiumminds/billy/gin/services/impl/pdf/FOPPDFTransformer.java";
        String outputPath = "/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/36859167815292f279e570d39dd2ddbcf1622dc6/attempt_1/transformed";

        if (args.length > 0) inputPath = args[0];
        if (args.length > 1) outputPath = args[1];

        Launcher launcher = new Launcher();
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/36859167815292f279e570d39dd2ddbcf1622dc6/billy/billy-gin/src/main/java/com/premiumminds/billy/gin/services/impl/pdf/FOPPDFTransformer.java");
        launcher.setSourceOutputDirectory("/home/kth/Documents/last_transformer/transformer-agent/reports1/gemini-3-pro-preview/36859167815292f279e570d39dd2ddbcf1622dc6/attempt_1/transformed");

        // --- CRITICAL IMPLEMENTATION RULE 1 & 2: Sniper Printer & NoClasspath ---
        launcher.getEnvironment().setCommentEnabled(true);
        launcher.getEnvironment().setPrettyPrinterCreator(
            () -> new SniperJavaPrettyPrinter(launcher.getEnvironment())
        );
        launcher.getEnvironment().setNoClasspath(true);
        // ------------------------------------------------------------------------

        launcher.addProcessor(new FopApiProcessor());

        try {
            System.out.println("Starting FOP Refactoring...");
            launcher.run();
            System.out.println("Refactoring complete. Output in: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}