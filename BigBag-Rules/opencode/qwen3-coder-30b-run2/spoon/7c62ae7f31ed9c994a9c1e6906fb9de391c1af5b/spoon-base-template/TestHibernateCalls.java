package test;

import org.hibernate.Query;
import org.hibernate.FlushMode;

public class TestHibernateCalls {
    
    public void testMethod(Query query) {
        // This would trigger the transformation
        query.setHibernateFlushMode(FlushMode.AUTO);
        
        // Another example
        query.setHibernateFlushMode(FlushMode.COMMIT);
    }
}