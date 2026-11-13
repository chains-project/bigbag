// Test file to demonstrate the transformation
public class TestTransformation {
    
    public void testKDTreeCreate() {
        // This should be transformed
        KDTree<String> tree1 = KDTree.create(2, (p1, p2) -> {
            return Math.sqrt(Math.pow(p1[0] - p2[0], 2) + Math.pow(p1[1] - p2[1], 2));
        });
        
        // This should also be transformed
        KDTree<Integer> tree2 = KDTree.create(3, PointDistanceFunction.L2);
        
        // This should NOT be transformed (only 1 argument)
        KDTree<Double> tree3 = KDTree.create(2);
        
        // This should NOT be transformed (different method name)
        SomeOtherClass.create(2, someFunction);
        
        // This should be transformed even with different variable names
        PointDistanceFunction customDist = (a, b) -> Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
        KDTree<Object> tree4 = KDTree.create(2, customDist);
    }
    
    public void testOtherDeprecatedMethods() {
        // If we extend the transformer, this could be fixed too
        QuadTreeKD tree = QuadTreeKD.create(2, 100);
        List results = tree.knnQuery(new double[]{1.0, 2.0}, 5);
    }
}