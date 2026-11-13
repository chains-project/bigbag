package github.chains;

import spoon.reflect.declaration.CtImport;
import spoon.reflect.factory.Factory;
import spoon.processing.AbstractProcessor;

public class FixTinspinImports extends AbstractProcessor<CtImport> {
    
    @Override
    public boolean isToBeProcessed(CtImport candidate) {
        // Process all imports
        return true;
    }
    
    @Override
    public void process(CtImport ctImport) {
        Factory factory = getFactory();
        
        // Fix imports that reference removed classes
        if (ctImport.getReference() != null) {
            String importFQCN = ctImport.getReference().toString();
            
            // Remove imports for classes that were removed in 2.0.0
            if ("org.tinspin.index.PointIndex".equals(importFQCN)) {
                ctImport.delete();
            } else if ("org.tinspin.index.PointDistanceFunction".equals(importFQCN)) {
                ctImport.delete();
            } else if ("org.tinspin.index.PointEntryDist".equals(importFQCN)) {
                ctImport.delete();
            }
        }
    }
}