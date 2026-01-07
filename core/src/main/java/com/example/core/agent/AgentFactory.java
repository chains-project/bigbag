package com.example.core.agent;

import com.example.core.agent.impl.JavaParserAgent;
import com.example.core.agent.impl.SpoonAgent;
import com.example.core.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating agent instances based on environment configuration.
 * 
 * Separates two concepts:
 * - LLM Agent (AGENT_NAME): The AI/LLM to use (e.g., "gemini", "copilot")
 * - Rule Generator (RULE_GENERATOR): The code transformation tool (e.g., "spoon", "openrewrite")
 * 
 * The factory creates a combination of LLM agent + rule generator.
 * This allows flexibility: gemini+spoon, copilot+spoon, gemini+openrewrite, etc.
 */
public class AgentFactory {
    
    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);
    
    // Registry of rule generator creators, keyed by generator name
    private static final Map<String, java.util.function.BiFunction<EnvConfig, String, BaseAgent>> RULE_GENERATOR_REGISTRY = new HashMap<>();
    
    static {
        // Register known rule generators
        // Each generator takes (EnvConfig, llmAgentName) and returns BaseAgent
        registerRuleGenerator("spoon", (envConfig, llmAgentName) -> new SpoonAgent(envConfig, llmAgentName));
        registerRuleGenerator("javaparser", (envConfig, llmAgentName) -> new JavaParserAgent(envConfig, llmAgentName));
        // Future generators can be registered here:
        // registerRuleGenerator("openrewrite", (envConfig, llmAgentName) -> new OpenRewriteAgent(envConfig, llmAgentName));
    }
    
    /**
     * Registers a rule generator creator function.
     * 
     * @param generatorName the name of the rule generator (e.g., "spoon", "openrewrite")
     * @param creator a function that creates an instance given (EnvConfig, llmAgentName)
     */
    public static void registerRuleGenerator(String generatorName, 
                                            java.util.function.BiFunction<EnvConfig, String, BaseAgent> creator) {
        RULE_GENERATOR_REGISTRY.put(generatorName.toLowerCase(), creator);
        log.debug("Registered rule generator: {}", generatorName);
    }
    
    /**
     * Creates an agent instance based on environment configuration.
     * 
     * Selection strategy:
     * 1. Get LLM agent name from AGENT_NAME (default: "gemini")
     * 2. Get rule generator from RULE_GENERATOR (default: auto-detect or "spoon")
     * 3. Create combination: LLM Agent + Rule Generator
     * 
     * @param envConfig the environment configuration
     * @return an instance of the appropriate agent
     * @throws IllegalStateException if the agent cannot be created or is invalid
     */
    public static BaseAgent createAgent(EnvConfig envConfig) {
        if (envConfig == null) {
            throw new IllegalArgumentException("EnvConfig cannot be null");
        }
        
        // Step 1: Get LLM agent name (e.g., "gemini", "copilot")
        String llmAgentName = envConfig.get("AGENT_NAME")
                .filter(name -> !name.isBlank())
                .orElse("gemini");  // Default to gemini
        
        log.info("Using LLM agent: {}", llmAgentName);
        
        // Step 2: Get rule generator name
        String ruleGeneratorName = determineRuleGenerator(envConfig);
        log.info("Using rule generator: {}", ruleGeneratorName);
        
        // Step 3: Create the agent with both LLM and rule generator
        return createAgent(llmAgentName, ruleGeneratorName, envConfig);
    }
    
    /**
     * Determines which rule generator to use based on environment variables.
     * 
     * @param envConfig the environment configuration
     * @return the rule generator name
     */
    private static String determineRuleGenerator(EnvConfig envConfig) {
        // Strategy 1: Explicit RULE_GENERATOR
        String explicitGenerator = envConfig.get("RULE_GENERATOR").orElse(null);
        if (explicitGenerator != null && !explicitGenerator.isBlank()) {
            return explicitGenerator.toLowerCase();
        }
        
        // Strategy 2: Auto-detect based on environment variables
        // Note: Both Spoon and JavaParser use BASE_TEMPLATE and API_DOCS
        // So we can't auto-detect based on these alone - explicit RULE_GENERATOR is required
        // If BASE_TEMPLATE/API_DOCS are present but no explicit generator, default to spoon for backward compatibility
        if (envConfig.get("BASE_TEMPLATE").isPresent() || envConfig.get("API_DOCS").isPresent()) {
            log.debug("BASE_TEMPLATE/API_DOCS detected. Defaulting to spoon (use RULE_GENERATOR=javaparser to use JavaParser)");
            return "spoon";
        }
        
        // Strategy 3: Default fallback
        log.debug("No rule generator specified, defaulting to: spoon");
        return "spoon";
    }
    
    /**
     * Creates an agent with the specified LLM agent and rule generator.
     * 
     * @param llmAgentName the LLM agent name (e.g., "gemini", "copilot")
     * @param ruleGeneratorName the rule generator name (e.g., "spoon", "openrewrite")
     * @param envConfig the environment configuration
     * @return the agent instance
     * @throws IllegalStateException if the rule generator is not found
     */
    private static BaseAgent createAgent(String llmAgentName, String ruleGeneratorName, EnvConfig envConfig) {
        java.util.function.BiFunction<EnvConfig, String, BaseAgent> creator = 
                RULE_GENERATOR_REGISTRY.get(ruleGeneratorName.toLowerCase());
        
        if (creator == null) {
            log.error("Rule generator '{}' not found in registry. Available generators: {}", 
                    ruleGeneratorName, RULE_GENERATOR_REGISTRY.keySet());
            throw new IllegalStateException("Rule generator not found: " + ruleGeneratorName);
        }
        
        try {
            BaseAgent agent = creator.apply(envConfig, llmAgentName);
            agent.validateEnvironment();
            log.info("Created agent: {} + {}", llmAgentName, ruleGeneratorName);
            return agent;
        } catch (Exception e) {
            log.error("Failed to create agent ({} + {}): {}", llmAgentName, ruleGeneratorName, e.getMessage(), e);
            throw new IllegalStateException("Failed to create agent: " + llmAgentName + " + " + ruleGeneratorName, e);
        }
    }
    
    /**
     * Gets a list of all registered rule generator names.
     * 
     * @return set of registered rule generator names
     */
    public static java.util.Set<String> getRegisteredRuleGenerators() {
        return new java.util.HashSet<>(RULE_GENERATOR_REGISTRY.keySet());
    }
}

