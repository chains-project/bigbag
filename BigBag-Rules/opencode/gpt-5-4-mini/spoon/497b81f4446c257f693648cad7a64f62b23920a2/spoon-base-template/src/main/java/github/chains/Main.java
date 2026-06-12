package github.chains;

import java.io.File;
import java.util.List;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public final class Main {

  private static final String OLD_IDENTITY_TYPE = "com.artipie.http.auth.Identities";
  private static final String NEW_AUTH_TYPE = "com.artipie.http.auth.Authentication";
  private static final String OLD_BASIC_IDENTITIES = "com.artipie.http.auth.BasicIdentities";
  private static final String OLD_SLICE_AUTH = "com.artipie.http.auth.SliceAuth";
  private static final String NEW_BASIC_AUTH_SLICE = "com.artipie.http.auth.BasicAuthSlice";

  private Main() {
  }

  public static void main(final String[] args) {
    if (args.length < 2) {
      System.err.println("Usage: Main <input-dir> <output-dir>");
      System.exit(1);
    }
    final Launcher launcher = new Launcher();
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.getEnvironment().setCommentEnabled(false);
    launcher.addInputResource(args[0]);
    launcher.setSourceOutputDirectory(new File(args[1]));
    final CtModel model = launcher.buildModel();
    rewrite(model);
    launcher.prettyprint();
  }

  private static void rewrite(final CtModel model) {
    for (final CtTypeReference<?> type : model.getElements(new TypeFilter<>(CtTypeReference.class))) {
      if (OLD_IDENTITY_TYPE.equals(type.getQualifiedName())) {
        type.replace(type.getFactory().Type().createReference(NEW_AUTH_TYPE));
      }
    }
    for (final CtFieldRead<?> read : model.getElements(new TypeFilter<>(CtFieldRead.class))) {
      final CtFieldReference<?> field = read.getVariable();
      if (field != null && "ANONYMOUS".equals(field.getSimpleName())
          && OLD_IDENTITY_TYPE.equals(field.getDeclaringType().getQualifiedName())) {
        field.getDeclaringType().replace(field.getFactory().Type().createReference(NEW_AUTH_TYPE));
      }
    }
    for (final CtConstructorCall<?> call : model.getElements(new TypeFilter<>(CtConstructorCall.class))) {
      rewriteConstructorCall(call);
    }
    for (final CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
      rewriteInvocation(invocation);
    }
  }

  private static void rewriteConstructorCall(final CtConstructorCall<?> call) {
    final CtTypeReference<?> type = call.getType();
    if (type == null) {
      return;
    }
    if (OLD_BASIC_IDENTITIES.equals(type.getQualifiedName()) && call.getArguments().size() == 1) {
      replaceExpression(call, cloneExpression(call.getArguments().get(0)));
      return;
    }
    if (OLD_SLICE_AUTH.equals(type.getQualifiedName()) && call.getArguments().size() == 3) {
      final CtExpression<?> origin = cloneExpression(call.getArguments().get(0));
      final CtExpression<?> permission = cloneExpression(call.getArguments().get(1));
      final CtExpression<?> identities = call.getArguments().get(2);
      final CtExpression<?> authentication = unwrapBasicIdentities(identities);
      call.setType(call.getFactory().Type().createReference(NEW_BASIC_AUTH_SLICE));
      call.setArguments(List.of(origin, authentication, permission));
    }
  }

  private static void rewriteInvocation(final CtInvocation<?> invocation) {
    final CtExecutableReference<?> executable = invocation.getExecutable();
    if (executable == null) {
      return;
    }
    final CtTypeReference<?> declaring = executable.getDeclaringType();
    if (declaring == null || !OLD_IDENTITY_TYPE.equals(declaring.getQualifiedName())) {
      return;
    }
    if ("user".equals(executable.getSimpleName())) {
      declaring.replace(declaring.getFactory().Type().createReference(NEW_AUTH_TYPE));
    }
  }

  private static CtExpression<?> unwrapBasicIdentities(final CtExpression<?> expression) {
    if (expression instanceof CtConstructorCall) {
      final CtConstructorCall<?> ctor = (CtConstructorCall<?>) expression;
      final CtTypeReference<?> type = ctor.getType();
      if (type != null && OLD_BASIC_IDENTITIES.equals(type.getQualifiedName()) && ctor.getArguments().size() == 1) {
        return cloneExpression(ctor.getArguments().get(0));
      }
    }
    return cloneExpression(expression);
  }

  @SuppressWarnings("unchecked")
  private static <T extends CtExpression<?>> T cloneExpression(final CtExpression<?> expression) {
    return (T) expression.clone();
  }

  private static void replaceExpression(final CtElement target, final CtExpression<?> replacement) {
    target.replace(replacement);
  }
}
