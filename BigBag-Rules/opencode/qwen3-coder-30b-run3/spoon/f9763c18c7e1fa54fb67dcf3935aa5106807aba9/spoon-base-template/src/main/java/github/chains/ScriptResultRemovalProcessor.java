package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

public class ScriptResultRemovalProcessor extends AbstractProcessor<CtInvocation<?>> {
    @Override
    public void process(CtInvocation<?> invocation) {
        // Check if this invocation creates a ScriptResult object
        CtExecutableReference<?> executableRef = invocation.getExecutable();
        if (executableRef != null && 
            executableRef.getSimpleName().equals("<init>")) {
            
            // Get the declaring type of the constructor
            if (executableRef.getDeclaringType() != null) {
                String declaringTypeName = executableRef.getDeclaringType().getQualifiedName();
                
                // If it's a ScriptResult constructor, we need to replace it
                if (declaringTypeName.contains("ScriptResult")) {
                    // Find the arguments passed to ScriptResult constructor
                    CtExpression<?>[] arguments = invocation.getArguments().toArray(new CtExpression[0]);
                    
                    if (arguments.length > 0) {
                        // The first argument is typically the result from executeScript()
                        // We want to replace the entire new ScriptResult(...) expression with just the first argument
                        invocation.replace(arguments[0]);
                    } else {
                        // No arguments, just remove the invocation
                        invocation.delete();
                    }
                }
            }
        }
    }
}
                }
            }
        }
    }
}