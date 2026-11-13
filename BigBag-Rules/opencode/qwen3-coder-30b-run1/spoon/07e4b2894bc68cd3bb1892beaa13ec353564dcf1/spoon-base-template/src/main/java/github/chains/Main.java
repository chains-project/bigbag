package github.chains;

import spoon.Launcher;
import spoon.processing.Processor;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtVariableWrite;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtUnaryOperator;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtTry;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtLambda;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtConditional;
import spoon.reflect.code.CtSwitch;
import spoon.reflect.code.CtSwitchExpression;
import spoon.reflect.code.CtCase;
import spoon.reflect.code.CtBreak;
import spoon.reflect.code.CtContinue;
import spoon.reflect.code.CtSynchronized;
import spoon.reflect.code.CtYield;
import spoon.reflect.code.CtFor;
import spoon.reflect.code.CtForEach;
import spoon.reflect.code.CtWhile;
import spoon.reflect.code.CtDo;
import spoon.reflect.code.CtAssert;
import spoon.reflect.code.CtAnnotationFieldAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtUnbox;
import spoon.reflect.code.CtBox;
import spoon.reflect.code.CtTargetedExpression;
import spoon.reflect.code.CtExecutableReferenceExpression;
import spoon.reflect.code.CtArrayRead;
import spoon.reflect.code.CtArrayWrite;
import spoon.reflect.code.CtArrayAccess;
import spoon.reflect.code.CtArrayDeclaration;
import spoon.reflect.code.CtVariableDeclaration;
import spoon.reflect.code.CtVariable;
import spoon.reflect.code.CtCatchVariable;
import spoon.reflect.code.CtStatementList;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtLineComment;
import spoon.reflect.code.CtBlockComment;
import spoon.reflect.code.CtJavaDoc;
import spoon.reflect.code.CtJavaDocTag;
import spoon.reflect.code.CtJavaDocParam;
import spoon.reflect.code.CtJavaDocReturn;
import spoon.reflect.code.CtJavaDocThrows;
import spoon.reflect.code.CtJavaDocSee;
import spoon.reflect.code.CtJavaDocLink;
import spoon.reflect.code.CtJavaDocLinkPlain;
import spoon.reflect.code.CtJavaDocText;
import spoon.reflect.code.CtJavaDocSnippet;
import spoon.reflect.code.CtJavaDocSnippetParam;
import spoon.reflect.code.CtJavaDocSnippetReturn;
import spoon.reflect.code.CtJavaDocSnippetThrows;
import spoon.reflect.code.CtJavaDocSnippetSee;
import spoon.reflect.code.CtJavaDocSnippetLink;
import spoon.reflect.code.CtJavaDocSnippetLinkPlain;
import spoon.reflect.code.CtJavaDocSnippetText;
import spoon.reflect.code.CtJavaDocSnippetSnippet;
import spoon.reflect.code.CtJavaDocSnippetSnippetParam;
import spoon.reflect.code.CtJavaDocSnippetSnippetReturn;
import spoon.reflect.code.CtJavaDocSnippetSnippetThrows;
import spoon.reflect.code.CtJavaDocSnippetSnippetSee;
import spoon.reflect.code.CtJavaDocSnippetSnippetLink;
import spoon.reflect.code.CtJavaDocSnippetSnippetLinkPlain;
import spoon.reflect.code.CtJavaDocSnippetSnippetText;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippet;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetParam;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetReturn;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetThrows;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetSee;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetLink;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetLinkPlain;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetText;
import spoon.reflect.code.CtJavaDocSnippetSnippetSnippetSnippet;

import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;

/**
 * Generic Spoon transformation to fix breaking changes in Jenkins acceptance test harness
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("Jenkins Acceptance Test Harness Breaking Change Fixer");
        
        // Process the code-coverage-api-plugin project
        processProject();
    }
    
    private static void processProject() {
        Launcher launcher = new Launcher();
        
        // Set the source folder to process
        launcher.addInputResource("/workspace/code-coverage-api-plugin/ui-tests/src/test/java");
        
        // Set the output folder
        launcher.setSourceOutputDirectory("/workspace/transformed-sources");
        
        // Add the Spoon Java processor
        launcher.addProcessor(new JenkinsAcceptanceTestFixer());
        
        // Build the model
        CtModel model = launcher.buildModel();
        
        // Process the model
        launcher.process();
        
        // Print results
        System.out.println("Transformation completed successfully.");
        System.out.println("Transformed sources are in /workspace/transformed-sources");
    }
}