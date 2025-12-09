import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.factory.Factory;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.util.Collections;
import java.util.List;
import java.io.File;

/**
 * Auto-generated Spoon driver to apply LLM-generated rules to:
 *   /home/kth/Documents/last_transformer/output/90ffd2cd31edecf778d14d0015da9ceab7e53081/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java
 * 
 * Raw rules file: reports1/gemini-3-pro-preview/90ffd2cd31edecf778d14d0015da9ceab7e53081/attempt_1/spoon-rules/__pay-adminusers__src__test__java__uk__gov__pay__adminusers__queue__event__EventMessageHandlerTest.java_prompt_4_spoon_rules_raw.txt
 * 
 * The transformed file will be written to: reports1/gemini-3-pro-preview/90ffd2cd31edecf778d14d0015da9ceab7e53081/attempt_1/transformed
 * (maintaining the original package directory structure)
 */
public class SpoonApplyRules {

    public static void main(String[] args) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.addInputResource("/home/kth/Documents/last_transformer/output/90ffd2cd31edecf778d14d0015da9ceab7e53081/pay-adminusers/src/test/java/uk/gov/pay/adminusers/queue/event/EventMessageHandlerTest.java");
        // Set output directory for transformed files
        launcher.setSourceOutputDirectory(new File("reports1/gemini-3-pro-preview/90ffd2cd31edecf778d14d0015da9ceab7e53081/attempt_1/transformed"));

        launcher.buildModel();
        CtModel model = launcher.getModel();
        Factory factory = launcher.getFactory();

        // Apply transformation rules
        applyTransformationRules(model, factory);

        launcher.process();
        launcher.prettyprint();
        System.out.println("Transformed files written to: reports1/gemini-3-pro-preview/90ffd2cd31edecf778d14d0015da9ceab7e53081/attempt_1/transformed");
    }

    private static void applyTransformationRules(CtModel model, Factory factory) {
        response:
        GenerateContentResponse(
        done=True,
        iterator=None,
        result=protos.GenerateContentResponse({
        "candidates": [
        {
        "content": {},
        "finish_reason": "STOP",
        "index": 0
        }
        ],
        "usage_metadata": {
        "prompt_token_count": 1756,
        "total_token_count": 9436
        },
        "model_version": "gemini-3-pro-preview"
        }),
        )
    }
}
