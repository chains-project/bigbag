package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
  private static final String OLD_TYPE = "org.apache.maven.shared.dependency.graph.internal.Maven31DependencyGraphBuilder";
  private static final String NEW_TYPE = "org.apache.maven.shared.dependency.graph.internal.DefaultDependencyGraphBuilder";
  private static final String RESOLVER_TYPE = "org.apache.maven.project.ProjectDependenciesResolver";

  public static void main(String[] args) {
    if (args.length < 1) {
      throw new IllegalArgumentException("Expected input source directory");
    }

    String input = args[0];
    String output = args.length > 1 ? args[1] : input;

    Launcher launcher = new Launcher();
    launcher.addInputResource(input);
    launcher.setSourceOutputDirectory(output);
    launcher.getEnvironment().setNoClasspath(true);
    launcher.getEnvironment().setAutoImports(true);
    launcher.buildModel();

    CtTypeReference<?> oldType = launcher.getFactory().Type().createReference(OLD_TYPE);
    CtTypeReference<?> newType = launcher.getFactory().Type().createReference(NEW_TYPE);
    CtTypeReference<?> resolverType = launcher.getFactory().Type().createReference(RESOLVER_TYPE);

    List<CtInvocation<?>> invocations = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtInvocation.class)));
    for (CtInvocation<?> invocation : invocations) {
      if (!"orElse".equals(invocation.getExecutable().getSimpleName()) || invocation.getArguments().size() != 1) {
        continue;
      }
      CtExpression<?> argument = invocation.getArguments().get(0);
      if (!(argument instanceof CtConstructorCall)) {
        continue;
      }
      CtConstructorCall<?> ctor = (CtConstructorCall<?>) argument;
      if (ctor.getType() == null || !oldType.getQualifiedName().equals(ctor.getType().getQualifiedName())) {
        continue;
      }
      CtInvocation<?> target = (CtInvocation<?>) invocation.getTarget();
      if (target == null || !"ofNullable".equals(target.getExecutable().getSimpleName()) || target.getArguments().isEmpty()) {
        continue;
      }
      invocation.replace(target.getArguments().get(0).clone());
    }

    List<CtConstructorCall<?>> calls = new ArrayList<>(launcher.getModel().getElements(new TypeFilter<>(CtConstructorCall.class)));
    for (CtConstructorCall<?> call : calls) {
      if (call.getType() == null || !oldType.getQualifiedName().equals(call.getType().getQualifiedName())) {
        continue;
      }

      CtExpression<?> resolver = findResolverExpression(call, resolverType);
      CtConstructorCall<?> replacement = launcher.getFactory().Core().createConstructorCall();
      replacement.setType(newType);
      if (call.getArguments() != null) {
        for (CtExpression<?> argument : call.getArguments()) {
          replacement.addArgument(argument.clone());
        }
      }
      if (replacement.getArguments().isEmpty() && resolver != null) {
        replacement.addArgument(resolver);
      }
      call.replace(replacement);
    }

    launcher.prettyprint();
  }

  private static CtExpression<?> findResolverExpression(CtElement element, CtTypeReference<?> resolverType) {
    CtClass<?> ctClass = element.getParent(CtClass.class);
    if (ctClass != null) {
      for (CtField<?> field : ctClass.getFields()) {
        if (field.getType() != null && resolverType.getQualifiedName().equals(field.getType().getQualifiedName())) {
          return element.getFactory().Code().createCodeSnippetExpression(field.getSimpleName());
        }
      }
    }

    CtMethod<?> method = element.getParent(CtMethod.class);
    if (method != null) {
      for (CtParameter<?> parameter : method.getParameters()) {
        if (parameter.getType() != null && resolverType.getQualifiedName().equals(parameter.getType().getQualifiedName())) {
          return element.getFactory().Code().createCodeSnippetExpression(parameter.getSimpleName());
        }
      }
    }

    return null;
  }
}
