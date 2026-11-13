package github.chains;

import liquibase.executor.ExecutorService;
import liquibase.statement.SqlStatement;

public class ExecutorServiceTest {
    public void testMethod() {
        // This should be transformed
        ExecutorService.getInstance().getExecutor(this).execute(new SqlStatement() {
            @Override
            public String[] getSqlStatements() {
                return new String[]{"SELECT 1"};
            }
            
            @Override
            public boolean continueOnError() {
                return false;
            }
        });
        
        // This should NOT be transformed (different pattern)
        ExecutorService.getInstance().execute(this, new SqlStatement() {
            @Override
            public String[] getSqlStatements() {
                return new String[]{"SELECT 1"};
            }
            
            @Override
            public boolean continueOnError() {
                return false;
            }
        });
    }
}