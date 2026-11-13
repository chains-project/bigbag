import com.gargoylesoftware.htmlunit.ScriptResult;
import org.jenkinsci.test.acceptance.po.PageObject;

public class ChartUtil {
    public static String getChartDataById(final PageObject pageObject, final String elementId) {
        if (isChartDisplayedByElementId(pageObject, elementId)) {
            Object result = pageObject.executeScript(String.format(
                    "delete(window.Array.prototype.toJSON) %n"
                            + "return JSON.stringify(echarts.getInstanceByDom(document.getElementById(\"%s\")).getOption())",
                    elementId));
            ScriptResult scriptResult = new ScriptResult(result);
            return scriptResult.getJavaScriptResult().toString();
        }
        return null;
    }
}