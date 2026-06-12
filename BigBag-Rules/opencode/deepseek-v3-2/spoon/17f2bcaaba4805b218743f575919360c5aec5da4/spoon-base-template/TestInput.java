import org.tinspin.index.kdtree.KDTree;
import org.tinspin.index.covertree.CoverTree;
import org.tinspin.index.PointDistanceFunction;

public class TestInput {
    
    public void testKDTree() {
        // This should be transformed: KDTree.create(2) -> KDTree.create(IndexConfig.create().setDimensions(2))
        KDTree<String> tree1 = KDTree.create(2);
        
        // This has a distance function parameter
        KDTree<String> tree2 = KDTree.create(2, (p1, p2) -> {
            double dx = p1[0] - p2[0];
            double dy = p1[1] - p2[1];
            return Math.sqrt(dx * dx + dy * dy);
        });
    }
    
    public void testCoverTree() {
        // This should be transformed: CoverTree.create(3, 2.0, distanceFunc) -> CoverTree.create(IndexConfig.create().setDimensions(3)...)
        PointDistanceFunction distFunc = PointDistanceFunction.L2;
        CoverTree<String> tree = CoverTree.create(3, 2.0, distFunc);
    }
    
    public void testOtherMethods() {
        // This should NOT be transformed (different method)
        String str = String.valueOf(42);
        
        // This should NOT be transformed (different class)
        java.util.ArrayList<String> list = new java.util.ArrayList<>(10);
    }
}