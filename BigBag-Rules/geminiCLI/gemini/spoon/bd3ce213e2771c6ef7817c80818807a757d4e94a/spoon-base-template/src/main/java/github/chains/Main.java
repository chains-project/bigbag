package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/OCR4all/src/main/java");
        launcher.setSourceOutputDirectory("/workspace/OCR4all/src/main/java");
        
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setComplianceLevel(8);

        launcher.buildModel();
        CtModel model = launcher.getModel();

        for (CtType<?> type : model.getAllTypes()) {
            if (type.getQualifiedName().equals("de.uniwue.helper.LineSegmentationHelper")) {
                modify(type, launcher.getFactory());
            }
            if (type.getQualifiedName().equals("de.uniwue.helper.RecognitionHelper")) {
                modify(type, launcher.getFactory());
            }
            if (type.getQualifiedName().equals("de.uniwue.helper.TrainingHelper")) {
                modify(type, launcher.getFactory());
            }
            if (type.getQualifiedName().equals("de.uniwue.feature.LibraryLoader")) {
                modifyLibraryLoader(type, launcher.getFactory());
            }
        }

        launcher.prettyprint();
    }

    private static void modifyLibraryLoader(CtType<?> type, Factory factory) {
        // Remove invalid import
        spoon.reflect.declaration.CtCompilationUnit cu = type.getPosition().getCompilationUnit();
        List<spoon.reflect.declaration.CtImport> imports = cu.getImports();
        spoon.reflect.declaration.CtImport toRemove = null;
        for (spoon.reflect.declaration.CtImport imp : imports) {
            if (imp.toString().contains("nu.pattern")) {
                 toRemove = imp;
                 break;
            }
        }
        if (toRemove != null) {
            imports.remove(toRemove);
            System.out.println("Removed import nu.pattern");
        }
        
        // Fix statement
        List<CtMethod<?>> methods = type.getMethodsByName("contextInitialized");
        if (!methods.isEmpty()) {
             CtMethod<?> method = methods.get(0);
             List<CtInvocation> invs = method.getElements(new TypeFilter<>(CtInvocation.class));
             for (CtInvocation inv : invs) {
                 if (inv.toString().contains("pattern.OpenCV.loadShared()")) {
                     CtCodeSnippetStatement snippet = factory.Code().createCodeSnippetStatement("nu.pattern.OpenCV.loadShared()");
                     inv.replace(snippet);
                     System.out.println("Fixed LibraryLoader statement");
                 }
             }
        }
    }

    private static void modify(CtType<?> type, Factory factory) {
        List<CtMethod<?>> methods = type.getMethodsByName("execute");
        if (methods.isEmpty()) {
            System.out.println("Method executeLineSegmentation not found.");
            return;
        }
        CtMethod method = methods.get(0);

        List<CtLocalVariable> vars = method.getElements(new TypeFilter<>(CtLocalVariable.class));
        CtLocalVariable writerVar = null;
        for (CtLocalVariable var : vars) {
            if (var.getSimpleName().equals("writer")) {
                writerVar = var;
                break;
            }
        }

        List<CtInvocation> invocations = method.getElements(new TypeFilter<>(CtInvocation.class));
        CtInvocation targetInvocation = null;
        for (CtInvocation inv : invocations) {
            if (inv.getExecutable().getSimpleName().equals("writeValue")) {
                // Check if target is 'writer'
                if (inv.getTarget() != null && inv.getTarget().toString().equals("writer")) {
                    targetInvocation = inv;
                    break;
                }
            }
        }

        if (writerVar != null && targetInvocation != null) {
            System.out.println("Found writer variable and writeValue invocation. Applying fix...");
            
            String reflectionCode = 
                "try {" +
                "    java.lang.reflect.Method writerMethod = com.fasterxml.jackson.databind.ObjectMapper.class.getMethod(\"writer\");" +
                "    Object writerObj = writerMethod.invoke(mapper);" +
                "    java.lang.reflect.Method writeValueMethod = writerObj.getClass().getMethod(\"writeValue\", java.io.File.class, java.lang.Object.class);" +
                "    writeValueMethod.invoke(writerObj, segmentListFile, dataList);" +
                "} catch (java.lang.Exception e) {" +
                "    e.printStackTrace();" +
                "}";
            
            CtCodeSnippetStatement snippet = factory.Code().createCodeSnippetStatement(reflectionCode);
            
            // We replace writerVar with snippet
            writerVar.replace(snippet);
            
            // And delete the invocation
            targetInvocation.delete();
            
            System.out.println("Fix applied.");
        } else {
            System.out.println("Could not find writer variable or writeValue invocation.");
        }
    }
}
