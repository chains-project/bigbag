package github.chains;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;

/**
 * Helper class to replace commons-beanutils PropertyUtils functionality
 * with java.beans standard library equivalents.
 */
public class PropertyUtilsHelper {
    
    /**
     * Replacement for PropertyUtils.getPropertyDescriptor(Object, String)
     */
    public static PropertyDescriptor getPropertyDescriptor(Object obj, String propertyName) throws Exception {
        if (obj == null || propertyName == null) {
            return null;
        }
        
        BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
        for (PropertyDescriptor pd : beanInfo.getPropertyDescriptors()) {
            if (pd.getName().equals(propertyName)) {
                return pd;
            }
        }
        return null;
    }
    
    /**
     * Replacement for PropertyUtils.getPropertyDescriptors(Class)
     * Note: The transformation directly uses Introspector.getBeanInfo(class).getPropertyDescriptors()
     * but this method is provided for completeness.
     */
    public static PropertyDescriptor[] getPropertyDescriptors(Class<?> clazz) throws Exception {
        BeanInfo beanInfo = Introspector.getBeanInfo(clazz);
        return beanInfo.getPropertyDescriptors();
    }
}