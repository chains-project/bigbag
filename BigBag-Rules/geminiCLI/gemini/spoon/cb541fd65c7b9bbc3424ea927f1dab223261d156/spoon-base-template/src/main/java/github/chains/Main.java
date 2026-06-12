package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.reflect.declaration.CtImport;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Launcher launcher = new Launcher();
        // Add only the problematic files to be parsed and printed
        launcher.addInputResource("/workspace/jcabi-http/src/main/java/com/jcabi/http/response/RestResponse.java");
        launcher.addInputResource("/workspace/jcabi-http/src/main/java/com/jcabi/http/mock/MkQueryMatchers.java");
        launcher.setSourceOutputDirectory("/workspace/jcabi-http/src/main/java");
        
        // Read classpath from file
        String cpStr = new String(Files.readAllBytes(Paths.get("/workspace/jcabi-http/cp-compile.txt"))).trim();
        String[] cpArray = cpStr.split(":");
        launcher.getEnvironment().setSourceClasspath(cpArray);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);

        CtModel model = launcher.buildModel();

        // Fix RestResponse.java
        List<CtClass> classes = model.getElements(new spoon.reflect.visitor.filter.NamedElementFilter<>(CtClass.class, "RestResponse"));
        if (!classes.isEmpty()) {
            CtClass<?> restResponse = classes.get(0);
            List<CtInvocation<?>> invocations = restResponse.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> inv : invocations) {
                CtExecutableReference<?> exec = inv.getExecutable();
                if (exec != null && exec.getDeclaringType() != null && "org.hamcrest.Matchers".equals(exec.getDeclaringType().getQualifiedName())) {
                    String methodName = exec.getSimpleName();
                    if ("hasItems".equals(methodName) || "notNullValue".equals(methodName)) {
                        inv.getTarget().replace(launcher.getFactory().createCodeSnippetExpression("org.hamcrest.CoreMatchers"));
                    } else if ("not".equals(methodName)) {
                        CtExpression<?> arg = inv.getArguments().get(0);
                        if (arg instanceof CtInvocation) {
                            CtInvocation<?> argInv = (CtInvocation<?>) arg;
                            if ("emptyIterableOf".equals(argInv.getExecutable().getSimpleName())) {
                                CtExpression<?> snippet = launcher.getFactory().createCodeSnippetExpression(
                                    "new org.hamcrest.CustomMatcher<Iterable<String>>(\"not empty iterable\") {\n" +
                                    "  @Override\n" +
                                    "  public boolean matches(Object item) {\n" +
                                    "    return item != null && ((Iterable<?>) item).iterator().hasNext();\n" +
                                    "  }\n" +
                                    "}"
                                );
                                inv.replace(snippet);
                            }
                        }
                    }
                }
            }
            restResponse.getPosition().getCompilationUnit().getImports().removeIf(i -> i.toString().contains("org.hamcrest.Matchers"));
        }
        
        // Fix MkQueryMatchers.java
        classes = model.getElements(new spoon.reflect.visitor.filter.NamedElementFilter<>(CtClass.class, "MkQueryMatchers"));
        if (!classes.isEmpty()) {
            CtClass<?> mkQueryMatchers = classes.get(0);
            List<CtInvocation<?>> invocations = mkQueryMatchers.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation<?> inv : invocations) {
                CtExecutableReference<?> exec = inv.getExecutable();
                if (exec != null && exec.getDeclaringType() != null && "org.hamcrest.Matchers".equals(exec.getDeclaringType().getQualifiedName())) {
                    if ("hasProperty".equals(exec.getSimpleName())) {
                        List<CtExpression<?>> arguments = inv.getArguments();
                        if (arguments.size() == 2) {
                            String propName = arguments.get(0).toString(); 
                            String paramName = arguments.get(1).toString();
                            String getter = propName.contains("Path") ? "getRawPath()" : "getRawQuery()";
                            String featureName = propName.contains("Path") ? "\"raw path\"" : "\"raw query\"";
                            CtExpression<?> snippet = launcher.getFactory().createCodeSnippetExpression(
                                "new org.hamcrest.FeatureMatcher<java.net.URI, String>(" + paramName + ", " + featureName + ", " + featureName + ") {\n" +
                                "  @Override\n" +
                                "  protected String featureValueOf(java.net.URI uri) {\n" +
                                "    return uri." + getter + ";\n" +
                                "  }\n" +
                                "}"
                            );
                            inv.replace(snippet);
                        }
                    }
                }
            }
            mkQueryMatchers.getPosition().getCompilationUnit().getImports().removeIf(i -> i.toString().contains("org.hamcrest.Matchers"));
        }
        
        launcher.prettyprint();
    }
}
