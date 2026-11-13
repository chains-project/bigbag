/**
 * GENERIC XENCHANTMENT API FIX TRANSFORMATION RULE
 * 
 * Problem: XEnchantment.parseEnchantment() method was removed in XSeries 8.6.0
 * 
 * Breaking Change:
 * - Old: XEnchantment.parseEnchantment("power") 
 * - New: XEnchantment.matchXEnchantment("power").get().getEnchant() OR 
 *        XEnchantment.matchXEnchantment("power").get() (if it returns Bukkit Enchantment directly)
 * 
 * Generic Transformation Pattern:
 * 
 * Find: XEnchantment.matchXEnchantment("...").get().parseEnchantment()
 * Replace: XEnchantment.matchXEnchantment("...").get() 
 * 
 * This rule can be applied to any Maven project using XSeries 8.6.0 with the same breaking change.
 * 
 * Files affected:
 * - WWCInventoryManager.java
 * - WWCTranslateGUIMainMenu.java 
 * - WWCTranslateGUIChatMenu.java
 * - WWCTranslateGUISourceLanguage.java
 * - WWCTranslateGUITargetLanguage.java
 * - ConfigurationMessagesOverridePossibleListGUI.java
 * 
 * The transformation replaces the deprecated API call chain with the correct one
 * that returns a Bukkit Enchantment object compatible with addEnchant().
 */