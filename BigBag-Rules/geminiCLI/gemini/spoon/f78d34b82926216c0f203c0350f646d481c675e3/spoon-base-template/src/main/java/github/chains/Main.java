package github.chains;

import spoon.Launcher;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.code.*;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        Factory factory = launcher.getFactory();
        
        CtClass<?> waveCreator = factory.Class().create("develop.p2p.lib.WaveCreator");
        waveCreator.addModifier(ModifierKind.PUBLIC);
        
        CtConstructor<?> ctor = factory.Core().createConstructor();
        ctor.addModifier(ModifierKind.PUBLIC);
        
        CtParameter<?> p1 = factory.Core().createParameter(); p1.setType(factory.Type().DOUBLE_PRIMITIVE); p1.setSimpleName("a");
        CtParameter<?> p2 = factory.Core().createParameter(); p2.setType(factory.Type().DOUBLE_PRIMITIVE); p2.setSimpleName("b");
        CtParameter<?> p3 = factory.Core().createParameter(); p3.setType(factory.Type().DOUBLE_PRIMITIVE); p3.setSimpleName("c");
        ctor.addParameter(p1);
        ctor.addParameter(p2);
        ctor.addParameter(p3);
        ctor.setBody(factory.Core().createBlock());
        waveCreator.addTypeMember(ctor);
        
        CtMethod<Double> getMethod = factory.Core().createMethod();
        getMethod.addModifier(ModifierKind.PUBLIC);
        getMethod.setType(factory.Type().DOUBLE_PRIMITIVE);
        getMethod.setSimpleName("get");
        
        CtParameter<?> p4 = factory.Core().createParameter(); p4.setType(factory.Type().DOUBLE_PRIMITIVE); p4.setSimpleName("a");
        CtParameter<?> p5 = factory.Core().createParameter(); p5.setType(factory.Type().BOOLEAN_PRIMITIVE); p5.setSimpleName("b");
        getMethod.addParameter(p4);
        getMethod.addParameter(p5);
        
        CtBlock<Double> getBody = factory.Core().createBlock();
        getBody.addStatement(factory.Code().createCodeSnippetStatement("return 0.0"));
        getMethod.setBody(getBody);
        waveCreator.addTypeMember(getMethod);
        
        CtMethod<Double> getStaticMethod = factory.Core().createMethod();
        getStaticMethod.addModifier(ModifierKind.PUBLIC);
        getStaticMethod.setType(factory.Type().DOUBLE_PRIMITIVE);
        getStaticMethod.setSimpleName("getStatic");
        CtBlock<Double> getStaticBody = factory.Core().createBlock();
        getStaticBody.addStatement(factory.Code().createCodeSnippetStatement("return 0.0"));
        getStaticMethod.setBody(getStaticBody);
        waveCreator.addTypeMember(getStaticMethod);
        
        CtClass<?> learnMath = factory.Class().create("develop.p2p.lib.LearnMath");
        learnMath.addModifier(ModifierKind.PUBLIC);
        
        CtMethod<Double> sigmoidMethod = factory.Core().createMethod();
        sigmoidMethod.addModifier(ModifierKind.PUBLIC);
        sigmoidMethod.addModifier(ModifierKind.STATIC);
        sigmoidMethod.setType(factory.Type().DOUBLE_PRIMITIVE);
        sigmoidMethod.setSimpleName("sigmoid");
        
        CtParameter<?> p6 = factory.Core().createParameter(); p6.setType(factory.Type().DOUBLE_PRIMITIVE); p6.setSimpleName("a");
        sigmoidMethod.addParameter(p6);
        
        CtBlock<Double> sigmoidBody = factory.Core().createBlock();
        sigmoidBody.addStatement(factory.Code().createCodeSnippetStatement("return a"));
        sigmoidMethod.setBody(sigmoidBody);
        learnMath.addTypeMember(sigmoidMethod);
        
        CtClass<?> leetConverter = factory.Class().create("develop.p2p.lib.LeetConverter");
        leetConverter.addModifier(ModifierKind.PUBLIC);
        
        CtMethod<String> convertMethod = factory.Core().createMethod();
        convertMethod.addModifier(ModifierKind.PUBLIC);
        convertMethod.addModifier(ModifierKind.STATIC);
        convertMethod.setType(factory.Type().STRING);
        convertMethod.setSimpleName("convert");
        
        CtParameter<?> p7 = factory.Core().createParameter(); p7.setType(factory.Type().STRING); p7.setSimpleName("a");
        convertMethod.addParameter(p7);
        
        CtBlock<String> convertBody = factory.Core().createBlock();
        convertBody.addStatement(factory.Code().createCodeSnippetStatement("return a"));
        convertMethod.setBody(convertBody);
        leetConverter.addTypeMember(convertMethod);

        launcher.getEnvironment().setSourceOutputDirectory(new File("/workspace/PeyangSuperbAntiCheat/src/main/java"));
        launcher.prettyprint();
        System.out.println("AST generated successfully.");
    }
}