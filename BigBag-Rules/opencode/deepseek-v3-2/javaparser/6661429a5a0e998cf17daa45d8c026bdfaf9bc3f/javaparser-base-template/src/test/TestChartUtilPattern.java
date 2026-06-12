import com.gargoylesoftware.htmlunit.ScriptResult;

public class TestChartUtilPattern {
    // Pattern from ChartUtil.java:35-36
    public String getChartDataByIdPattern(Object result) {
        ScriptResult scriptResult = new ScriptResult(result);
        return scriptResult.getJavaScriptResult().toString();
    }
    
    // Pattern from ChartUtil.java:60
    public Object getDataPattern(Object result) {
        Object scriptResult = new ScriptResult(result).getJavaScriptResult();
        return scriptResult;
    }
    
    // Should transform to:
    // public String getChartDataByIdPattern(Object result) {
    //     Object scriptResult = result;
    //     return result.toString();
    // }
    // 
    // public Object getDataPattern(Object result) {
    //     Object scriptResult = result;
    //     return result;
    // }
}