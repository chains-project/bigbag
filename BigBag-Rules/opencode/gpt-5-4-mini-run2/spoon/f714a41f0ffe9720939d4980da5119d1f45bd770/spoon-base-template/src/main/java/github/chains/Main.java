package github.chains;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

    private static final String OLD_CONFIGURATION = "com.pubnub.api.PNConfiguration";
    private static final String NEW_USER_ID = "com.pubnub.api.UserId";
    private static final String OLD_CALLBACK = "com.pubnub.api.callbacks.SubscribeCallback";
    private static final String NEW_CALLBACK_BASE = "com.pubnub.api.callbacks.SubscribeCallback.BaseSubscribeCallback";
    private static final String OLD_USER_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult";
    private static final String NEW_UUID_RESULT = "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult";
    private static final String OLD_SPACE_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult";
    private static final String NEW_CHANNEL_RESULT = "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult";
    private static final String OLD_MEMBERSHIP_RESULT = "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult";
    private static final String NEW_MEMBERSHIP_RESULT = "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult";

    public static void main(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Expected source directory path as the first argument");
        }

        File input = new File(args[0]);
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setSourceOutputDirectory(input);
        addSourceRoots(launcher, input);
        launcher.buildModel();

        CtModel model = launcher.getModel();
        transformConstructorCalls(launcher, model);
        transformPubNubCallbacks(launcher, model);

        launcher.prettyprint();
    }

    private static void addSourceRoots(Launcher launcher, File input) {
        if (!input.isDirectory()) {
            launcher.addInputResource(input.getPath());
            return;
        }

        try {
            Files.walk(input.toPath())
                .filter(path -> path.toString().endsWith(".java"))
                .filter(path -> path.toString().contains("/src/main/java/") || path.toString().contains("/src/test/java/"))
                .forEach(path -> launcher.addInputResource(path.toString()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void transformConstructorCalls(Launcher launcher, CtModel model) {
        CtTypeReference<?> configurationType = launcher.getFactory().Type().createReference(OLD_CONFIGURATION);
        CtTypeReference<?> userIdType = launcher.getFactory().Type().createReference(NEW_USER_ID);

        List<CtConstructorCall<?>> calls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : calls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CONFIGURATION.equals(type.getQualifiedName()) || !call.getArguments().isEmpty()) {
                continue;
            }

            CtExpression<String> emptyString = launcher.getFactory().Code().createLiteral("");
            CtConstructorCall<?> userIdCall = launcher.getFactory().Code().createConstructorCall(userIdType, emptyString);
            CtConstructorCall<?> replacement = launcher.getFactory().Code().createConstructorCall(configurationType, userIdCall);
            call.replace(replacement);
        }
    }

    private static void transformPubNubCallbacks(Launcher launcher, CtModel model) {
        List<CtMethod<?>> methods = model.getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod<?> method : methods) {
            if (method.getParameters().size() != 2) {
                continue;
            }

            String name = method.getSimpleName();
            String parameterType = method.getParameters().get(1).getType().getQualifiedName();

            if ("user".equals(name) && OLD_USER_RESULT.equals(parameterType)) {
                method.setSimpleName("uuid");
                method.getParameters().get(1).setType(launcher.getFactory().Type().createReference(NEW_UUID_RESULT));
            } else if ("space".equals(name) && OLD_SPACE_RESULT.equals(parameterType)) {
                method.setSimpleName("channel");
                method.getParameters().get(1).setType(launcher.getFactory().Type().createReference(NEW_CHANNEL_RESULT));
            } else if ("membership".equals(name) && OLD_MEMBERSHIP_RESULT.equals(parameterType)) {
                method.getParameters().get(1).setType(launcher.getFactory().Type().createReference(NEW_MEMBERSHIP_RESULT));
            }
        }

        List<CtConstructorCall<?>> callbackCalls = model.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall<?> call : callbackCalls) {
            CtTypeReference<?> type = call.getType();
            if (type == null || !OLD_CALLBACK.equals(type.getQualifiedName())) {
                continue;
            }
            call.setType(launcher.getFactory().Type().createReference(NEW_CALLBACK_BASE));
        }

        List<CtNewClass<?>> newClasses = model.getElements(new TypeFilter<>(CtNewClass.class));
        for (CtNewClass<?> newClass : newClasses) {
            if (newClass.getAnonymousClass() == null) {
                continue;
            }
            CtTypeReference<?> superType = newClass.getAnonymousClass().getSuperclass();
            if (superType == null || (!OLD_CALLBACK.equals(superType.getQualifiedName()) && !NEW_CALLBACK_BASE.equals(superType.getQualifiedName()))) {
                continue;
            }
            if (!newClass.getAnonymousClass().getMethodsByName("file").isEmpty()) {
                continue;
            }

            CtMethod<?> template = newClass.getAnonymousClass().getMethods().isEmpty() ? null : newClass.getAnonymousClass().getMethods().iterator().next();
            if (template == null) {
                continue;
            }

            CtMethod<?> fileMethod = template.clone();
            fileMethod.setSimpleName("file");
            fileMethod.setType(launcher.getFactory().Type().VOID_PRIMITIVE);
            fileMethod.getParameters().clear();
            fileMethod.setBody(launcher.getFactory().Core().createBlock());

            CtParameter<?> pubnubParam = launcher.getFactory().Core().createParameter();
            pubnubParam.setSimpleName("pubnub");
            pubnubParam.setType(launcher.getFactory().Type().createReference("com.pubnub.api.PubNub"));
            fileMethod.addParameter(pubnubParam);

            CtParameter<?> fileParam = launcher.getFactory().Core().createParameter();
            fileParam.setSimpleName("pnFileEventResult");
            fileParam.setType(launcher.getFactory().Type().createReference("com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult"));
            fileMethod.addParameter(fileParam);

            newClass.getAnonymousClass().addMethod(fileMethod);
        }

        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> invocation : invocations) {
            if (invocation.getExecutable() == null || invocation.getExecutable().getDeclaringType() == null) {
                continue;
            }

            String declaringType = invocation.getExecutable().getDeclaringType().getQualifiedName();
            String methodName = invocation.getExecutable().getSimpleName();
            List<CtExpression<?>> arguments = new ArrayList<>(invocation.getArguments());

            if (OLD_CONFIGURATION.equals(declaringType) && "setUuid".equals(methodName) && arguments.size() == 1) {
                CtExpression<?> uuidArg = arguments.get(0);
                CtTypeReference<?> userIdRef = launcher.getFactory().Type().createReference(NEW_USER_ID);
                CtConstructorCall<?> userIdCall = launcher.getFactory().Code().createConstructorCall(userIdRef, uuidArg.clone());
                invocation.getExecutable().setSimpleName("setUserId");
                invocation.setArguments(List.of(userIdCall));
            }
        }
    }
}
