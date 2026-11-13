package github.chains;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class Main {

  private static final String PN_CONFIGURATION = "com.pubnub.api.PNConfiguration";
  private static final String USER_ID = "com.pubnub.api.UserId";
  private static final String SUBSCRIBE_CALLBACK = "com.pubnub.api.callbacks.SubscribeCallback";
  private static final String BASE_SUBSCRIBE_CALLBACK =
      "com.pubnub.api.callbacks.SubscribeCallback.BaseSubscribeCallback";
  private static final String OLD_PN_USER_RESULT =
      "com.pubnub.api.models.consumer.pubsub.objects.PNUserResult";
  private static final String OLD_PN_SPACE_RESULT =
      "com.pubnub.api.models.consumer.pubsub.objects.PNSpaceResult";
  private static final String OLD_PN_MEMBERSHIP_RESULT =
      "com.pubnub.api.models.consumer.pubsub.objects.PNMembershipResult";
  private static final String NEW_PN_CHANNEL_RESULT =
      "com.pubnub.api.models.consumer.objects_api.channel.PNChannelMetadataResult";
  private static final String NEW_PN_MEMBERSHIP_RESULT =
      "com.pubnub.api.models.consumer.objects_api.membership.PNMembershipResult";
  private static final String NEW_PN_UUID_RESULT =
      "com.pubnub.api.models.consumer.objects_api.uuid.PNUUIDMetadataResult";
  private static final String NEW_PN_FILE_RESULT =
      "com.pubnub.api.models.consumer.pubsub.files.PNFileEventResult";

  public static void main(String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: Main <input-source-dir> <output-source-dir>");
      System.exit(1);
    }

    Path inputDir = Paths.get(args[0]);
    Path outputDir = Paths.get(args[1]);

    Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(true);
    launcher.addInputResource(inputDir.toString());
    launcher.setSourceOutputDirectory(outputDir.toFile());
    launcher.buildModel();

    Factory factory = launcher.getFactory();
    CtTypeReference<?> userIdRef = factory.Type().createReference(USER_ID);
    CtTypeReference<?> subscribeCallbackRef = factory.Type().createReference(SUBSCRIBE_CALLBACK);
    CtTypeReference<?> baseSubscribeCallbackRef = factory.Type().createReference(BASE_SUBSCRIBE_CALLBACK);

    for (CtNewClass<?> newClass : launcher.getModel().getElements(new TypeFilter<>(CtNewClass.class))) {
      if (isType(newClass.getType(), SUBSCRIBE_CALLBACK)) {
        newClass.setType(baseSubscribeCallbackRef);
      }

      CtClass<?> anonymousClass = newClass.getAnonymousClass();
      if (anonymousClass != null) {
        for (CtTypeMember member : anonymousClass.getTypeMembers()) {
          if (member instanceof CtMethod<?>) {
            renameCallbackMethod((CtMethod<?>) member, factory);
          }
        }
      }
    }

    for (var method : launcher.getModel().getElements(new TypeFilter<>(spoon.reflect.declaration.CtMethod.class))) {
      if (method.getParent(CtNewClass.class) != null) {
        renameCallbackMethod(method, factory);
      }
    }

    for (CtConstructorCall<?> constructorCall : launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class))) {
      if (!isType(constructorCall.getType(), PN_CONFIGURATION)) {
        continue;
      }

      if (constructorCall.getArguments().isEmpty()) {
        constructorCall.addArgument(createFallbackUserId(factory, userIdRef));
        continue;
      }

      if (constructorCall.getArguments().size() == 1) {
        CtExpression<?> argument = constructorCall.getArguments().get(0);
        if (!isType(argument.getType(), USER_ID)) {
          constructorCall.setArguments(Collections.singletonList(wrapAsUserId(factory, userIdRef, argument)));
        }
      }
    }

    for (CtInvocation<?> invocation : launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class))) {
      if (!"setUuid".equals(invocation.getExecutable().getSimpleName())) {
        continue;
      }

      if (!isType(invocation.getTarget() == null ? null : invocation.getTarget().getType(), PN_CONFIGURATION)) {
        continue;
      }

      if (invocation.getArguments().size() != 1) {
        continue;
      }

      CtExpression<?> argument = invocation.getArguments().get(0);
      invocation.getArguments().set(0, isType(argument.getType(), USER_ID) ? argument.clone() : wrapAsUserId(factory, userIdRef, argument));
      invocation.getExecutable().setSimpleName("setUserId");
    }

    launcher.prettyprint();
  }

  private static CtExpression<?> createFallbackUserId(Factory factory, CtTypeReference<?> userIdRef) {
    return wrapAsUserId(
        factory,
        userIdRef,
        factory.Code().createCodeSnippetExpression("PubNub.generateUUID()"));
  }

  private static CtConstructorCall<?> wrapAsUserId(
      Factory factory, CtTypeReference<?> userIdRef, CtExpression<?> value) {
    CtConstructorCall<?> userIdCall = factory.Code().createConstructorCall(userIdRef);
    userIdCall.addArgument(value.clone());
    return userIdCall;
  }

  private static boolean isType(CtTypeReference<?> type, String qualifiedName) {
    return type != null && qualifiedName.equals(type.getQualifiedName());
  }

  private static void renameCallbackMethod(spoon.reflect.declaration.CtMethod<?> method, Factory factory) {
    if (method.getParameters().size() != 2) {
      return;
    }

    var parameter = method.getParameters().get(1);
    if ("user".equals(method.getSimpleName()) && isType(parameter.getType(), OLD_PN_USER_RESULT)) {
      method.setSimpleName("uuid");
      parameter.setType(factory.Type().createReference(NEW_PN_UUID_RESULT));
    } else if ("space".equals(method.getSimpleName()) && isType(parameter.getType(), OLD_PN_SPACE_RESULT)) {
      method.setSimpleName("channel");
      parameter.setType(factory.Type().createReference(NEW_PN_CHANNEL_RESULT));
    } else if ("membership".equals(method.getSimpleName())
        && isType(parameter.getType(), OLD_PN_MEMBERSHIP_RESULT)) {
      parameter.setType(factory.Type().createReference(NEW_PN_MEMBERSHIP_RESULT));
    }
  }

}
