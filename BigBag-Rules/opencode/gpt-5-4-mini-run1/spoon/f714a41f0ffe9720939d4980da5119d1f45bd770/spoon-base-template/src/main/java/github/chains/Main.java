package github.chains;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {
    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Usage: Main <maven-project-dir> [output-dir]");
        }

        String inputDir = args[0];
        String outputDir = args.length > 1 ? args[1] : inputDir + "/spooned";

        Launcher launcher = new Launcher();
        launcher.addInputResource(inputDir);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.getEnvironment().setIgnoreDuplicateDeclarations(true);
        launcher.buildModel();

        Factory factory = launcher.getFactory();

        for (CtConstructorCall<?> call : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
            if (isType(call.getType(), "com.pubnub.api.PNConfiguration") && call.getArguments().isEmpty()) {
                call.replace(factory.Code().createCodeSnippetExpression(
                        "new com.pubnub.api.PNConfiguration(new com.pubnub.api.UserId(java.util.UUID.randomUUID().toString()))"));
            }
        }

        for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
            CtTypeReference<?> declaringType = invocation.getExecutable().getDeclaringType();
            if (!isType(declaringType, "com.pubnub.api.PNConfiguration")) {
                continue;
            }

            String name = invocation.getExecutable().getSimpleName();
            if ("setUuid".equals(name) && invocation.getArguments().size() == 1) {
                invocation.replace(factory.Code().createCodeSnippetExpression(
                        "(" + invocation.getTarget() + ").setUserId(new com.pubnub.api.UserId(" + invocation.getArguments().get(0) + "))"));
            } else if ("getUuid".equals(name) && invocation.getArguments().isEmpty()) {
                invocation.replace(factory.Code().createCodeSnippetExpression(
                        "(" + invocation.getTarget() + ").getUserId().getValue()"));
            }
        }

        for (CtNewClass<?> newClass : launcher.getModel().getElements(new TypeFilter<>(CtNewClass.class))) {
            if (!isType(newClass.getType(), "com.pubnub.api.callbacks.SubscribeCallback")) {
                continue;
            }

            newClass.setType(factory.Type().createReference("com.pubnub.api.callbacks.SubscribeCallback.BaseSubscribeCallback"));

            CtClass<?> anon = newClass.getAnonymousClass();
            if (anon == null) {
                continue;
            }

            for (CtMethod<?> method : anon.getMethods()) {
                if ("user".equals(method.getSimpleName()) || "space".equals(method.getSimpleName())) {
                    method.delete();
                    continue;
                }

                if ("membership".equals(method.getSimpleName()) && method.getParameters().size() == 1
                        && isType(method.getParameters().get(0).getType(), "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult")) {
                    method.getParameters().get(0).setType(factory.Type().createReference("com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult"));
                }
            }
        }

        new spoon.reflect.visitor.ImportCleaner().process(launcher.getModel().getRootPackage());

        launcher.setSourceOutputDirectory(new File(outputDir));
        launcher.prettyprint();

        cleanupGeneratedSources(new File(outputDir));
    }

    private static boolean isType(CtTypeReference<?> type, String qualifiedName) {
        return type != null && qualifiedName.equals(type.getQualifiedName());
    }

    private static void cleanupGeneratedSources(File outputDir) {
        try {
            Files.walk(outputDir.toPath())
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(Main::cleanupFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void cleanupFile(Path path) {
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            content = content.replace(
                    "        PNConfiguration pnConfiguration = new com.pubnub.api.PNConfiguration(new com.pubnub.api.UserId(java.util.UUID.randomUUID().toString()));\n",
                    "        PNConfiguration pnConfiguration;\n"
                            + "        try {\n"
                            + "            pnConfiguration = new com.pubnub.api.PNConfiguration(new com.pubnub.api.UserId(java.util.UUID.randomUUID().toString()));\n"
                            + "        } catch (com.pubnub.api.PubNubException e) {\n"
                            + "            throw new RuntimeException(e);\n"
                            + "        }\n");
            content = content.replace("import com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult;",
                    "import com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult;");
            content = content.replace("import com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult;",
                    "import com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult;");
            content = content.replace("import com.pubnub.api.models.consumer.pubsub.objects.PNUserResult;",
                    "import com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult;");
            content = content.replace("info.bitrich.xchangestream.service.pubnub.ex.printStackTrace();", "ex.printStackTrace();");
            content = Pattern.compile("(?s)\\n\\s*@Override\\n\\s*public void user\\([^}]*?\\n\\s*}\\n").matcher(content).replaceAll("\n");
            content = Pattern.compile("(?s)\\n\\s*@Override\\n\\s*public void space\\([^}]*?\\n\\s*}\\n").matcher(content).replaceAll("\n");
            Files.writeString(path, content, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
