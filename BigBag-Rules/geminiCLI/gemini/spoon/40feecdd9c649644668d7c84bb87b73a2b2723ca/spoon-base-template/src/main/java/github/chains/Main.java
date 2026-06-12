package github.chains;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtTry;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        // Set input source
        launcher.addInputResource("/workspace/CoursesPortlet/courses-portlet-api/src/main/java");
        
        // Output to the same directory to overwrite
        launcher.setSourceOutputDirectory("/workspace/CoursesPortlet/courses-portlet-api/src/main/java");
        
        // Configure environment
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setCommentEnabled(true);
        
        // Build model
        launcher.buildModel();
        CtModel model = launcher.getModel();

        // Locate the specific class
        List<CtClass> classes = model.getElements(new TypeFilter<>(CtClass.class));
        for (CtClass clazz : classes) {
            if (clazz.getQualifiedName().equals("org.jasig.portlet.courses.model.xml.CourseMeetingWrapper")) {
                CtMethod method = (CtMethod) clazz.getMethodsByName("getFormattedMeetingTime").get(0);
                
                List<CtTry> tryBlocks = method.getElements(new TypeFilter<>(CtTry.class));
                for (CtTry tryBlock : tryBlocks) {
                    List<CtCatch> catchers = tryBlock.getCatchers();
                    CtCatch ioExceptionCatch = null;
                    
                    for (CtCatch catchBlock : catchers) {
                        if (catchBlock.getParameter().getType().getSimpleName().equals("IOException")) {
                            ioExceptionCatch = catchBlock;
                            break;
                        }
                    }
                    
                    if (ioExceptionCatch != null) {
                        tryBlock.removeCatcher(ioExceptionCatch);
                        // Unwrap if empty
                        if (tryBlock.getCatchers().isEmpty() && tryBlock.getFinalizer() == null) {
                            tryBlock.replace(tryBlock.getBody().getStatements());
                        }
                    }
                }
            }
        }
        
        // Save changes (only modified files are usually written, or all if configured. 
        // In recent Spoon, prettyprint() might write everything. 
        // But since we want to be safe, overwriting is fine as we are transforming.)
        launcher.prettyprint();
    }
}
