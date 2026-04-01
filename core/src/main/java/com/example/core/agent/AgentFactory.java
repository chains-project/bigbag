package com.example.core.agent;

import com.example.core.agent.impl.GeminiAgent;
import com.example.core.agent.impl.OpenCodeAgent;
import com.example.core.config.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating agent instances based on environment configuration.
 *
 * Separates two concepts:
 * - LLM Agent (AGENT_NAME): The AI/LLM tool to use (e.g., "gemini", "opencode")
 * - Rule Generator (RULE_GENERATOR): The code transformation tool (e.g., "spoon", "javaparser")
 *
 * The factory selects the agent implementation that matches the combination of
 * LLM agent and rule generator. Supported combinations:
 *
 * <pre>
 *   AGENT_NAME=gemini   + RULE_GENERATOR=spoon       → GeminiAgent(spoon)
 *   AGENT_NAME=gemini   + RULE_GENERATOR=javaparser   → GeminiAgent(javaparser)
 *   AGENT_NAME=opencode + RULE_GENERATOR=spoon       → OpenCodeAgent(spoon)
 *   AGENT_NAME=opencode + RULE_GENERATOR=javaparser   → OpenCodeAgent(javaparser)
 * </pre>
 *
 * Each agent implementation encapsulates the CLI command syntax specific to its
 * LLM tool (Gemini CLI vs OpenCode CLI flags differ).
 */
public class AgentFactory {

    private static final Logger log = LoggerFactory.getLogger(AgentFactory.class);

    // Composite key: "<llmAgentName>+<ruleGeneratorName>" → agent creator
    private static final Map<String, java.util.function.BiFunction<EnvConfig, String, BaseAgent>> AGENT_REGISTRY = new HashMap<>();

    // Fallback registry keyed only by rule generator name (used when AGENT_NAME is unknown)
    private static final Map<String, java.util.function.BiFunction<EnvConfig, String, BaseAgent>> RULE_GENERATOR_REGISTRY = new HashMap<>();

    static {
        // Gemini-based agents (use Gemini CLI: gemini --model X --yolo --debug -o json -p)
        registerAgent("gemini", "spoon",      (env, llm) -> new GeminiAgent(env, llm, GeminiAgent.GENERATOR_SPOON));
        registerAgent("gemini", "javaparser", (env, llm) -> new GeminiAgent(env, llm, GeminiAgent.GENERATOR_JAVAPARSER));

        // OpenCode-based agents (use OpenCode CLI: opencode --model X -p)
        registerAgent("opencode", "spoon",      (env, llm) -> new OpenCodeAgent(env, llm, OpenCodeAgent.GENERATOR_SPOON));
        registerAgent("opencode", "javaparser", (env, llm) -> new OpenCodeAgent(env, llm, OpenCodeAgent.GENERATOR_JAVAPARSER));

        // Default fallbacks by rule generator (used when AGENT_NAME does not match any known LLM)
        registerRuleGenerator("spoon",      (env, llm) -> new GeminiAgent(env, llm, GeminiAgent.GENERATOR_SPOON));
        registerRuleGenerator("javaparser", (env, llm) -> new GeminiAgent(env, llm, GeminiAgent.GENERATOR_JAVAPARSER));
    }
    
    /**
     * Registers an agent creator for a specific LLM + rule generator combination.
     *
     * @param llmAgentName    the LLM agent name (e.g., "gemini", "opencode")
     * @param generatorName   the rule generator name (e.g., "spoon", "javaparser")
     * @param creator         a function that creates an instance given (EnvConfig, llmAgentName)
     */
    public static void registerAgent(String llmAgentName, String generatorName,
                                     java.util.function.BiFunction<EnvConfig, String, BaseAgent> creator) {
        String key = compositeKey(llmAgentName, generatorName);
        AGENT_REGISTRY.put(key, creator);
        log.debug("Registered agent: {} + {}", llmAgentName, generatorName);
    }

    /**
     * Registers a fallback rule generator creator (used when AGENT_NAME is unknown).
     *
     * @param generatorName the rule generator name (e.g., "spoon", "javaparser")
     * @param creator       a function that creates an instance given (EnvConfig, llmAgentName)
     */
    public static void registerRuleGenerator(String generatorName,
                                             java.util.function.BiFunction<EnvConfig, String, BaseAgent> creator) {
        RULE_GENERATOR_REGISTRY.put(generatorName.toLowerCase(), creator);
        log.debug("Registered fallback rule generator: {}", generatorName);
    }

    /**
     * Creates an agent instance based on environment configuration.
     *
     * Selection strategy:
     * 1. Read AGENT_NAME (default: "gemini") and RULE_GENERATOR (default: "spoon")
     * 2. Look up the exact combination in AGENT_REGISTRY
     * 3. Fall back to RULE_GENERATOR_REGISTRY if the combination is not registered
     *
     * @param envConfig the environment configuration
     * @return an instance of the appropriate agent
     * @throws IllegalStateException if the agent cannot be created
     */
    public static BaseAgent createAgent(EnvConfig envConfig) {
        if (envConfig == null) {
            throw new IllegalArgumentException("EnvConfig cannot be null");
        }

        String llmAgentName = envConfig.get("AGENT_NAME")
                .filter(name -> !name.isBlank())
                .orElse("gemini");

        String ruleGeneratorName = determineRuleGenerator(envConfig);

        log.info("Creating agent: LLM={} RuleGenerator={}", llmAgentName, ruleGeneratorName);

        return createAgent(llmAgentName, ruleGeneratorName, envConfig);
    }

    /**
     * Determines the rule generator to use based on environment variables.
     *
     * @param envConfig the environment configuration
     * @return the rule generator name (lowercase)
     */
    private static String determineRuleGenerator(EnvConfig envConfig) {
        String explicit = envConfig.get("RULE_GENERATOR").orElse(null);
        if (explicit != null && !explicit.isBlank()) {
            return explicit.toLowerCase();
        }

        // Backward-compatible default: prefer spoon when template env vars are present
        if (envConfig.get("BASE_TEMPLATE").isPresent() || envConfig.get("API_DOCS").isPresent()) {
            log.debug("BASE_TEMPLATE/API_DOCS detected. Defaulting to spoon rule generator.");
            return "spoon";
        }

        log.debug("No rule generator specified, defaulting to: spoon");
        return "spoon";
    }

    /**
     * Creates an agent for the given LLM + rule generator combination.
     *
     * Lookup order:
     * 1. Exact match in AGENT_REGISTRY (e.g., "opencode+spoon")
     * 2. Fallback to RULE_GENERATOR_REGISTRY (passes llmAgentName through)
     *
     * @param llmAgentName    the LLM agent name (e.g., "gemini", "opencode")
     * @param ruleGeneratorName the rule generator name (e.g., "spoon", "javaparser")
     * @param envConfig       the environment configuration
     * @return the agent instance
     * @throws IllegalStateException if no matching agent is found
     */
    private static BaseAgent createAgent(String llmAgentName, String ruleGeneratorName, EnvConfig envConfig) {
        // Try exact LLM+generator combination first
        java.util.function.BiFunction<EnvConfig, String, BaseAgent> creator =
                AGENT_REGISTRY.get(compositeKey(llmAgentName, ruleGeneratorName));

        // Fall back to generator-only registry
        if (creator == null) {
            log.warn("No specific agent registered for '{}+{}'. Falling back to rule generator registry.",
                    llmAgentName, ruleGeneratorName);
            creator = RULE_GENERATOR_REGISTRY.get(ruleGeneratorName.toLowerCase());
        }

        if (creator == null) {
            log.error("No agent found for LLM='{}' + generator='{}'. Registered agents: {}",
                    llmAgentName, ruleGeneratorName, AGENT_REGISTRY.keySet());
            throw new IllegalStateException(
                    "No agent registered for: " + llmAgentName + " + " + ruleGeneratorName);
        }

        try {
            BaseAgent agent = creator.apply(envConfig, llmAgentName);
            agent.validateEnvironment();
            log.info("Created agent: {} + {}", llmAgentName, ruleGeneratorName);
            return agent;
        } catch (Exception e) {
            log.error("Failed to create agent ({} + {}): {}", llmAgentName, ruleGeneratorName, e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to create agent: " + llmAgentName + " + " + ruleGeneratorName, e);
        }
    }

    /**
     * Returns all registered LLM + rule generator combinations.
     *
     * @return set of composite keys in the form "llm+generator"
     */
    public static java.util.Set<String> getRegisteredAgents() {
        return new java.util.HashSet<>(AGENT_REGISTRY.keySet());
    }

    /**
     * Returns all registered rule generator names (fallback registry).
     *
     * @return set of rule generator names
     */
    public static java.util.Set<String> getRegisteredRuleGenerators() {
        return new java.util.HashSet<>(RULE_GENERATOR_REGISTRY.keySet());
    }

    private static String compositeKey(String llmAgentName, String generatorName) {
        return llmAgentName.toLowerCase() + "+" + generatorName.toLowerCase();
    }
}

