package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("../pitest-mutation-testing-elements-plugin/src/main/java");
        launcher.getEnvironment().setNoClasspath(true); 
        launcher.getEnvironment().setAutoImports(true);
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // 1. Modify MutationTestSummaryData
        CtClass<?> summaryData = getClazz(model, "org.pitest.elements.models.MutationTestSummaryData");
        
        // Change field Set<ClassInfo> classes -> Set<ClassName> classes
        CtField<?> classesField = summaryData.getField("classes");
        CtTypeReference<?> classNameType = launcher.getFactory().Type().createReference("org.pitest.classinfo.ClassName");
        CtTypeReference<?> setClassName = launcher.getFactory().Type().createReference(Set.class);
        setClassName.addActualTypeArgument(classNameType);
        classesField.setType((CtTypeReference) setClassName);
        
        // Change constructor
        CtConstructor<?> constructor = summaryData.getConstructors().iterator().next();
        CtParameter<?> classesParam = constructor.getParameters().get(2); // 3rd param
        CtTypeReference<?> colClassName = launcher.getFactory().Type().createReference(Collection.class);
        colClassName.addActualTypeArgument(classNameType);
        classesParam.setType((CtTypeReference) colClassName);

        // Update getPackageName
        CtMethod<?> getPackageName = getMethod(summaryData, "getPackageName");
        List<CtInvocation<?>> invocations = getPackageName.getElements(new TypeFilter<>(CtInvocation.class));
        for (CtInvocation<?> inv : invocations) {
            if (inv.getExecutable().getSimpleName().equals("getName")) {
                 inv.replace(inv.getTarget());
            }
        }
        
        CtMethod<?> getClasses = getMethod(summaryData, "getClasses");
        getClasses.setType((CtTypeReference) colClassName);


        // 2. Modify MutationReportListener
        CtClass<?> listener = getClazz(model, "org.pitest.elements.MutationReportListener");
        CtMethod<?> createSummaryData = getMethod(listener, "createSummaryData");

        List<CtConstructorCall> constCalls = createSummaryData.getElements(new TypeFilter<>(CtConstructorCall.class));
        for (CtConstructorCall call : constCalls) {
            if (call.getType().getQualifiedName().equals("org.pitest.elements.models.MutationTestSummaryData")) {
                CtExpression<?> thirdArg = (CtExpression<?>) call.getArguments().get(2);
                if (thirdArg instanceof CtInvocation) {
                    CtInvocation inv = (CtInvocation) thirdArg;
                    if (inv.getExecutable().getSimpleName().equals("getClassInfo")) {
                        CtExpression<?> arg = (CtExpression<?>) inv.getArguments().get(0);
                        thirdArg.replace(arg);
                    }
                }
            }
        }


        // 3. Modify JsonParser
        CtClass<?> jsonParser = getClazz(model, "org.pitest.elements.utils.JsonParser");
        
        CtMethod<?> getLines = getMethod(jsonParser, "getLines");
        for (CtLocalVariable<?> var : getLines.getElements(new TypeFilter<>(CtLocalVariable.class))) {
             if (var.getSimpleName().equals("classes")) {
                 var.setType((CtTypeReference) colClassName);
             }
        }

        CtMethod<?> findReaderForSource = getMethod(jsonParser, "findReaderForSource");
        findReaderForSource.getParameters().get(0).setType((CtTypeReference) colClassName);

        CtMethod<?> classInfoToNames = getMethod(jsonParser, "classInfoToNames");
        classInfoToNames.getParameters().get(0).setType((CtTypeReference) colClassName);
        
        List<CtLambda> lambdas = classInfoToNames.getElements(new TypeFilter<>(CtLambda.class));
        for (CtLambda lambda : lambdas) {
             List<CtInvocation<?>> invs = lambda.getElements(new TypeFilter<>(CtInvocation.class));
             for (CtInvocation<?> inv : invs) {
                 if (inv.getExecutable().getSimpleName().equals("getName")) {
                     inv.replace(inv.getTarget());
                 }
             }
        }

        launcher.setSourceOutputDirectory("../pitest-mutation-testing-elements-plugin/src/main/java");
        launcher.prettyprint();
        System.out.println("Transformation done.");
    }
    
    private static CtClass<?> getClazz(CtModel model, String name) {
        List<CtClass<?>> list = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> element) {
                return element.getQualifiedName().equals(name);
            }
        });
        if (list.isEmpty()) throw new RuntimeException("Class not found: " + name);
        return list.get(0);
    }
    
    private static CtMethod<?> getMethod(CtClass<?> clazz, String name) {
        for (CtMethod<?> m : clazz.getMethods()) {
            if (m.getSimpleName().equals(name)) return m;
        }
        throw new RuntimeException("Method not found: " + name + " in " + clazz.getQualifiedName());
    }
}