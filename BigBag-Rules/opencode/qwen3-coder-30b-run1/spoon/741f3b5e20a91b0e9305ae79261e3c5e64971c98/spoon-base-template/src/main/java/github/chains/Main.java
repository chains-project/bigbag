package github.chains;

import spoon.Launcher;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtTry;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        // Check if arguments are provided
        if (args.length < 1) {
            System.err.println("Usage: java Main <source_directory>");
            System.exit(1);
        }
        
        File sourceDirectory = new File(args[0]);
        
        Launcher launcher = new Launcher();
        
        // Set the source input directory
        launcher.addInputResource(sourceDirectory);
        
        // Set the output directory
        launcher.getEnvironment().setSourceOutputDirectory("target/spooned");
        
        // Compile the source code to create the model
        launcher.buildModel();
        
        // Get the factory
        Factory factory = launcher.getFactory();
        
        // Find all try-catch blocks
        List<CtTry> tryBlocks = factory.Package().getRootPackage().getElements(new TypeFilter<>(CtTry.class));
        
        // Process each try-catch block
        for (CtTry tryBlock : tryBlocks) {
            // Look for catch blocks that catch StreamReadException
            for (CtCatch catchBlock : tryBlock.getCatchers()) {
                CtTypeReference<? extends Throwable> catchType = catchBlock.getParameter().getType();
                
                // Check if the catch block catches the old StreamReadException
                if ("com.fasterxml.jackson.core.exc.StreamReadException".equals(catchType.getQualifiedName())) {
                    // Replace with JsonProcessingException which is the correct replacement
                    CtTypeReference<Exception> newExceptionType = factory.Type().createReference("com.fasterxml.jackson.core.JsonProcessingException");
                    catchBlock.getParameter().setType(newExceptionType);
                }
            }
        }
        
        // Save the changes
        launcher.prettyprint();
    }
}