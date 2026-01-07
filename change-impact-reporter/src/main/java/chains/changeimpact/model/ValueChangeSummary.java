package chains.changeimpact.model;

import com.example.japicmp.model.ValueChange;

/**
 * Captures value before/after pairs with a flag indicating a change.
 */
public record ValueChangeSummary(String oldValue, String newValue, boolean changed) {

    public static ValueChangeSummary from(ValueChange<String> valueChange) {
        if (valueChange == null) {
            return null;
        }
        return new ValueChangeSummary(valueChange.oldValue(), valueChange.newValue(), valueChange.changed());
    }
}

