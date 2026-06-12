package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;
import java.util.ArrayList;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.addInputResource("/workspace/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/zip/ByteArrayOutputStream.java");
        launcher.addInputResource("/workspace/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/zip/AbstractZipUnArchiver.java");
        launcher.addInputResource("/workspace/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/jar/JarToolModularJarArchiver.java");
        launcher.addInputResource("/workspace/plexus-archiver/src/main/java/org/codehaus/plexus/archiver/zip/OffloadingOutputStream.java");
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.buildModel();
        
        CtModel model = launcher.getModel();

        // 1. ByteArrayOutputStream
        CtClass<?> baOs = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> c) {
                return "org.codehaus.plexus.archiver.zip.ByteArrayOutputStream".equals(c.getQualifiedName());
            }
        }).get(0);
        
        for (CtMethod<?> m : baOs.getMethodsByName("toInputStream")) {
            List<CtReturn> rets = m.getElements(new TypeFilter<>(CtReturn.class));
            for (CtReturn ret : rets) {
                if (ret.getReturnedExpression().toString().contains("ClosedInputStream")) {
                    ret.setReturnedExpression(launcher.getFactory().Code().createCodeSnippetExpression("new java.io.ByteArrayInputStream(new byte[0])"));
                }
            }
        }
        List<CtImport> imps1 = new ArrayList<>(launcher.getFactory().CompilationUnit().getOrCreate(baOs).getImports());
        imps1.removeIf(imp -> imp.toString().contains("ClosedInputStream"));
        launcher.getFactory().CompilationUnit().getOrCreate(baOs).setImports(imps1);

        // 2. AbstractZipUnArchiver
        CtClass<?> azUa = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> c) {
                return "org.codehaus.plexus.archiver.zip.AbstractZipUnArchiver".equals(c.getQualifiedName());
            }
        }).get(0);
        
        for (CtMethod<?> m : azUa.getMethodsByName("execute")) {
            List<CtLocalVariable> vars = m.getElements(new TypeFilter<>(CtLocalVariable.class));
            for (CtLocalVariable var : vars) {
                if (var.getType().getSimpleName().equals("BoundedInputStream")) {
                    var.setType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.BoundedInputStream"));
                    if (var.getDefaultExpression() instanceof CtConstructorCall) {
                        ((CtConstructorCall) var.getDefaultExpression()).getExecutable().setDeclaringType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.BoundedInputStream"));
                        ((CtConstructorCall) var.getDefaultExpression()).setType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.BoundedInputStream"));
                    }
                }
                if (var.getType().getSimpleName().equals("CountingInputStream")) {
                    var.setType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.CountingInputStream"));
                    if (var.getDefaultExpression() instanceof CtConstructorCall) {
                        ((CtConstructorCall) var.getDefaultExpression()).getExecutable().setDeclaringType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.CountingInputStream"));
                        ((CtConstructorCall) var.getDefaultExpression()).setType(launcher.getFactory().Type().createReference("org.apache.commons.compress.utils.CountingInputStream"));
                    }
                }
            }
            List<CtInvocation> invs = m.getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation inv : invs) {
                if (inv.getExecutable().getSimpleName().equals("getByteCount")) {
                    inv.getExecutable().setSimpleName("getBytesRead");
                }
            }
        }
        List<CtImport> imps2 = new ArrayList<>(launcher.getFactory().CompilationUnit().getOrCreate(azUa).getImports());
        imps2.removeIf(imp -> imp.toString().contains("org.apache.commons.io.input"));
        launcher.getFactory().CompilationUnit().getOrCreate(azUa).setImports(imps2);

        // 3. JarToolModularJarArchiver
        CtClass<?> jtmJa = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> c) {
                return "org.codehaus.plexus.archiver.jar.JarToolModularJarArchiver".equals(c.getQualifiedName());
            }
        }).get(0);
        
        List<CtFieldRead> fieldReads = jtmJa.getElements(new TypeFilter<>(CtFieldRead.class));
        for (CtFieldRead fr : fieldReads) {
            if (fr.getVariable().getSimpleName().equals("NULL_PRINT_STREAM")) {
                fr.replace(launcher.getFactory().Code().createCodeSnippetExpression(
                    "new java.io.PrintStream(new java.io.OutputStream() { @java.lang.Override public void write(int b) {} })"
                ));
            }
        }
        List<CtImport> imps3 = new ArrayList<>(launcher.getFactory().CompilationUnit().getOrCreate(jtmJa).getImports());
        imps3.removeIf(imp -> imp.toString().contains("NullPrintStream"));
        launcher.getFactory().CompilationUnit().getOrCreate(jtmJa).setImports(imps3);

        // 4. OffloadingOutputStream
        CtClass<?> oOs = model.getElements(new TypeFilter<CtClass<?>>(CtClass.class) {
            @Override
            public boolean matches(CtClass<?> c) {
                return "org.codehaus.plexus.archiver.zip.OffloadingOutputStream".equals(c.getQualifiedName());
            }
        }).get(0);
        
        oOs.setSuperclass(launcher.getFactory().Type().createReference("java.io.OutputStream"));
        
        CtField<Integer> tField = launcher.getFactory().Core().createField();
        tField.setSimpleName("threshold");
        tField.setType(launcher.getFactory().Type().INTEGER_PRIMITIVE);
        tField.addModifier(ModifierKind.PRIVATE);
        oOs.addFieldAtTop(tField);
        
        CtField<Integer> wField = launcher.getFactory().Core().createField();
        wField.setSimpleName("written");
        wField.setType(launcher.getFactory().Type().INTEGER_PRIMITIVE);
        wField.addModifier(ModifierKind.PRIVATE);
        wField.setDefaultExpression((CtExpression) launcher.getFactory().Code().createCodeSnippetExpression("0"));
        oOs.addFieldAtTop(wField);
        
        for (CtConstructor<?> ctor : oOs.getConstructors()) {
            if (ctor.getParameters().size() == 5) {
                ctor.getParameters().get(0).setSimpleName("thr");
                ctor.getBody().insertBegin((CtStatement) launcher.getFactory().Code().createCodeSnippetStatement("threshold = thr").compile());
                List<CtInvocation> invs = ctor.getBody().getElements(new TypeFilter<>(CtInvocation.class));
                for (CtInvocation inv : invs) {
                    if (inv.getExecutable().getSimpleName().equals("<init>")) {
                        inv.delete();
                    }
                }
            }
        }
        
        for (CtMethod<?> m : oOs.getMethodsByName("getStream")) {
            for (CtAnnotation<?> a : new ArrayList<>(m.getAnnotations())) {
                if (a.getAnnotationType().getSimpleName().equals("Override")) m.removeAnnotation(a);
            }
        }
        for (CtMethod<?> m : oOs.getMethodsByName("thresholdReached")) {
            for (CtAnnotation<?> a : new ArrayList<>(m.getAnnotations())) {
                if (a.getAnnotationType().getSimpleName().equals("Override")) m.removeAnnotation(a);
            }
        }

        // Add method via snippet parsing
        CtClass<?> dummy = launcher.getFactory().Code().createCodeSnippetStatement(
            "class Dummy extends java.io.OutputStream {\n" +
            "    private int threshold;\n" +
            "    private int written = 0;\n" +
            "    public Dummy(int threshold) { this.threshold = threshold; }\n" +
            "    protected void thresholdReached() throws java.io.IOException {}\n" +
            "    protected java.io.OutputStream getStream() throws java.io.IOException { return null; }\n" +
            "    protected void checkThreshold(int count) throws java.io.IOException {\n" +
            "        if (written + count > threshold) {\n" +
            "            thresholdReached();\n" +
            "            threshold = Integer.MAX_VALUE;\n" +
            "        }\n" +
            "    }\n" +
            "    @java.lang.Override\n" +
            "    public void write(int b) throws java.io.IOException {\n" +
            "        checkThreshold(1);\n" +
            "        getStream().write(b);\n" +
            "        written++;\n" +
            "    }\n" +
            "    @java.lang.Override\n" +
            "    public void write(byte[] b) throws java.io.IOException {\n" +
            "        checkThreshold(b.length);\n" +
            "        getStream().write(b);\n" +
            "        written += b.length;\n" +
            "    }\n" +
            "    @java.lang.Override\n" +
            "    public void write(byte[] b, int off, int len) throws java.io.IOException {\n" +
            "        checkThreshold(len);\n" +
            "        getStream().write(b, off, len);\n" +
            "        written += len;\n" +
            "    }\n" +
            "    @java.lang.Override\n" +
            "    public void flush() throws java.io.IOException {\n" +
            "        getStream().flush();\n" +
            "    }\n" +
            "}"
        ).compile();

        for (CtMethod<?> m : dummy.getMethods()) {
            if (m.getSimpleName().equals("checkThreshold") || m.getSimpleName().equals("write") || m.getSimpleName().equals("flush")) {
                oOs.addMethod(m.clone());
            }
        }

        for (CtMethod<?> m : oOs.getMethodsByName("close")) {
            List<CtInvocation> invs = m.getBody().getElements(new TypeFilter<>(CtInvocation.class));
            for (CtInvocation inv : invs) {
                if (inv.getExecutable().getSimpleName().equals("close") && inv.getTarget() != null && inv.getTarget().toString().equals("super")) {
                    inv.delete();
                }
            }
        }
        
        List<CtImport> imps4 = new ArrayList<>(launcher.getFactory().CompilationUnit().getOrCreate(oOs).getImports());
        imps4.removeIf(imp -> imp.toString().contains("ThresholdingOutputStream") || imp.toString().contains("Wrapper"));
        launcher.getFactory().CompilationUnit().getOrCreate(oOs).setImports(imps4);

        spoon.support.JavaOutputProcessor processor = new spoon.support.JavaOutputProcessor(launcher.createPrettyPrinter());
        processor.setFactory(launcher.getFactory());
        processor.getEnvironment().setSourceOutputDirectory(new java.io.File("/workspace/plexus-archiver/src/main/java"));
        processor.process(baOs);
        processor.process(azUa);
        processor.process(jtmJa);
        processor.process(oOs);
        
        System.out.println("Done.");
    }
}
