package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtFieldReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.LifecyclePhase;

public class JavadocToAnnotationProcessor extends AbstractProcessor<CtClass<?>> {
    @Override
    public void process(CtClass<?> ctClass) {
        if (!"GenerateMojo".equals(ctClass.getSimpleName())) {
            return;
        }

        // Process Class Javadoc
        String classJavadoc = ctClass.getDocComment();
        if (classJavadoc != null && classJavadoc.contains("@goal")) {
             String goal = extractTagValue(classJavadoc, "@goal");
             String phase = extractTagValue(classJavadoc, "@phase");
             
             // Add @Mojo annotation
             CtAnnotation<Mojo> mojoAnn = getFactory().createAnnotation(getFactory().createCtTypeReference(Mojo.class));
             mojoAnn.addValue("name", goal);
             if (phase != null && "generate-sources".equals(phase)) {
                  CtTypeReference<LifecyclePhase> lifecyclePhaseRef = getFactory().createCtTypeReference(LifecyclePhase.class);
                  CtFieldReference enumValRef = getFactory().createFieldReference();
                  enumValRef.setDeclaringType(lifecyclePhaseRef);
                  enumValRef.setSimpleName("GENERATE_SOURCES");
                  enumValRef.setType(lifecyclePhaseRef);
                  enumValRef.setStatic(true);
                  
                  CtFieldRead fieldRead = getFactory().createFieldRead();
                  fieldRead.setVariable(enumValRef);
                  
                  mojoAnn.addValue("defaultPhase", fieldRead);
             }
             
             ctClass.addAnnotation(mojoAnn);
             
             // Remove tags from Javadoc
             String newJavadoc = removeTags(classJavadoc, "@goal", "@phase");
             ctClass.setDocComment(newJavadoc);
        }
        
        // Process Fields
        for (CtField<?> field : ctClass.getFields()) {
             String fieldDoc = field.getDocComment();
             if (fieldDoc != null && fieldDoc.contains("@parameter")) {
                 CtAnnotation<Parameter> paramAnn = getFactory().createAnnotation(getFactory().createCtTypeReference(Parameter.class));
                 
                 String expression = extractTagAttribute(fieldDoc, "@parameter", "expression");
                 String defaultValue = extractTagAttribute(fieldDoc, "@parameter", "default-value");
                 
                 boolean isRequired = false;
                 boolean isReadOnly = false;
                 
                 if (expression != null) {
                     if (expression.contains("${")) {
                         paramAnn.addValue("defaultValue", expression);
                         if (expression.equals("${project}")) {
                              isReadOnly = true;
                              isRequired = true;
                         }
                     } else {
                         paramAnn.addValue("property", expression);
                     }
                 }
                 
                 if (defaultValue != null) {
                      paramAnn.addValue("defaultValue", defaultValue);
                 }
                 
                 if (fieldDoc.contains("@required")) {
                      isRequired = true;
                 }
                 
                 if (fieldDoc.contains("@readonly")) {
                      isReadOnly = true;
                 }
                 
                 if (isRequired) paramAnn.addValue("required", true);
                 if (isReadOnly) paramAnn.addValue("readonly", true);
                 
                 field.addAnnotation(paramAnn);
                 
                 // Remove tags from Javadoc
                 String newFieldDoc = removeTags(fieldDoc, "@parameter", "@required", "@readonly");
                 field.setDocComment(newFieldDoc);
             }
        }
    }
    
    private String extractTagValue(String javadoc, String tagName) {
        Pattern p = Pattern.compile(tagName + "\\s+(\\S+)");
        Matcher m = p.matcher(javadoc);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String extractTagAttribute(String javadoc, String tagName, String attribute) {
        String[] lines = javadoc.split("\\r?\\n");
        for (String line : lines) {
            if (line.contains(tagName)) {
                 Pattern p = Pattern.compile(attribute + "\\s*=\\s*\"([^\"]*)\"");
                 Matcher m = p.matcher(line);
                 if (m.find()) return m.group(1);
                 
                 p = Pattern.compile(attribute + "\\s*=\\s*([^\\s]*)");
                 m = p.matcher(line);
                 if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    private String removeTags(String javadoc, String... tags) {
        StringBuilder sb = new StringBuilder();
        String[] lines = javadoc.split("\\r?\\n");
        for (String line : lines) {
             boolean remove = false;
             for (String tag : tags) {
                  if (line.contains(tag)) {
                       String trimmed = line.trim();
                       if (trimmed.startsWith("* " + tag) || trimmed.startsWith(tag) || trimmed.startsWith("*" + tag)) {
                           remove = true;
                           break;
                       }
                  }
             }
             if (!remove) {
                 sb.append(line).append(System.lineSeparator());
             }
        }
        return sb.toString().trim();
    }
}
