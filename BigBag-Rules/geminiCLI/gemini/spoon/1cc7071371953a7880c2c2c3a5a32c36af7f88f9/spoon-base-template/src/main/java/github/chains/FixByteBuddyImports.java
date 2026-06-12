package github.chains;

import spoon.processing.AbstractProcessor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtImport;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtPackageReference;

public class FixByteBuddyImports extends AbstractProcessor<CtElement> {
    @Override
    public boolean isToBeProcessed(CtElement candidate) {
        if (candidate instanceof CtImport) {
            CtImport imp = (CtImport) candidate;
            if (imp.getReference() != null) {
                String refName = imp.getReference().toString();
                if (refName.startsWith("org.assertj.core.internal.bytebuddy")) {
                    return true;
                }
            }
        }
        if (candidate instanceof CtTypeReference) {
             CtTypeReference<?> ref = (CtTypeReference<?>) candidate;
             if (ref.getPackage() != null) {
                return ref.getPackage().getQualifiedName().startsWith("org.assertj.core.internal.bytebuddy");
             }
        }
        return false;
    }

    @Override
    public void process(CtElement element) {
        if (element instanceof CtImport) {
            System.out.println("Deleting import: " + element);
            element.delete();
        } else if (element instanceof CtTypeReference) {
            CtTypeReference<?> ref = (CtTypeReference<?>) element;
            CtPackageReference pkg = ref.getPackage();
            String oldPkgName = pkg.getQualifiedName();
            String newPkgName = oldPkgName.replace("org.assertj.core.internal.bytebuddy", "net.bytebuddy");
            
            System.out.println("Updating package from " + oldPkgName + " to " + newPkgName);
            CtPackageReference newPkg = getFactory().Core().createPackageReference();
            newPkg.setSimpleName(newPkgName);
            
            ref.setPackage(newPkg);
        }
    }
}
