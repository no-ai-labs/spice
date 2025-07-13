package io.github.spice.agents

import io.github.spice.*
import io.github.spice.tools.*

/**
 * 🌶️ PlanningAgent Usage Guide
 * 
 * Complete guide showing different ways to use PlanningAgent in Spice Framework
 */
object PlanningUsageGuide {
    
    /**
     * 📖 Usage Examples and Best Practices
     */
    fun printUsageGuide() {
        println("""
        🌶️ SPICE PLANNING AGENT USAGE GUIDE
        ===================================
        
        The PlanningAgent is a flexible component in Spice Framework that converts
        user goals into structured, executable plans. Here's how to use it:
        
        
        1️⃣ BASIC USAGE - Standalone Agent
        ================================
        
        ```kotlin
        // Create a planning agent with default settings
        val planningAgent = PlanningAgent(
            baseAgent = OpenAIAgent(apiKey = "your-api-key")
        )
        
        // Optional: Register with AgentEngine (not required!)
        agentEngine.registerAgent(planningAgent)
        
        // Use directly
        val message = Message(
            content = "Create a mobile app for food delivery",
            sender = "user"
        )
        
        val plan = planningAgent.processMessage(message)
        println(plan.content)
        ```
        
        
        2️⃣ CUSTOMIZATION - Company & Domain Specific
        ===========================================
        
        ```kotlin
        // Startup-focused planning
        val startupPrompt = CompanyPromptBuilder(
            companyTemplate = "Focus on MVP and rapid iteration",
            domainSpecificRules = mapOf(
                "mobile_app" to "Cross-platform development priority"
            )
        )
        
        val startupPlanner = PlanningAgent(
            baseAgent = vertexAgent,
            promptBuilder = startupPrompt,
            config = PlanningConfig(
                maxSteps = 8,
                outputFormat = OutputFormat.STRUCTURED_PLAN,
                planningStrategy = PlanningStrategy.ITERATIVE
            )
        )
        ```
        
        
        3️⃣ TOOL INTEGRATION - Use by Other Agents
        ========================================
        
        ```kotlin
        // Wrap as a tool for other agents to use
        val planningTool = PlanningTool(planningAgent)
        
        // Add to any agent
        myAgent.addTool(planningTool)
        
        // Or use extension function
        myAgent.addPlanningCapability(
            baseAgent = openaiAgent,
            config = PlanningConfig(outputFormat = OutputFormat.JSON)
        )
        
        // Use in agent's processMessage
        val result = executeTool("planning", mapOf(
            "goal" to "Optimize database performance",
            "context" to mapOf("domain" to "data_analysis")
        ))
        ```
        
        
        4️⃣ SPRING BOOT AUTO-CONFIGURATION
        ================================
        
        ```yaml
        # application.yml
        spice:
          planning:
            enabled: true
            auto-register: true
            company-profile: startup
            max-steps: 8
            planning-strategy: ITERATIVE
            domain-rules:
              web_development: "Include responsive design"
              mobile_app: "Cross-platform priority"
        ```
        
        ```kotlin
        // Automatically configured!
        @Autowired
        private lateinit var planningAgent: PlanningAgent
        
        @Autowired
        private lateinit var planningTool: PlanningTool
        ```
        
        
        5️⃣ ADVANCED CUSTOMIZATION
        ========================
        
        ```kotlin
        // Custom prompt builder
        val customPrompt = object : PromptBuilder {
            override fun buildPrompt(
                goal: String, 
                context: PlanningContext, 
                config: PlanningConfig
            ): String {
                return "Your custom planning logic: ${'$'}goal"
            }
        }
        
        // Multiple output formats
        val jsonPlanner = PlanningAgent(
            baseAgent = anthropicAgent,
            config = PlanningConfig(outputFormat = OutputFormat.JSON)
        )
        
        val markdownPlanner = PlanningAgent(
            baseAgent = vertexAgent,
            config = PlanningConfig(outputFormat = OutputFormat.MARKDOWN)
        )
        ```
        
        
        6️⃣ CONTEXT-AWARE PLANNING
        ========================
        
        ```kotlin
        val message = Message(
            content = "Build an e-commerce platform",
            sender = "user",
            metadata = mapOf(
                "company_profile" to "enterprise",
                "domain" to "web_development",
                "priority" to "high",
                "deadline" to "6 months",
                "resources" to "React, Node.js, PostgreSQL",
                "constraints" to "Security compliance, GDPR"
            )
        )
        
        val contextualPlan = planningAgent.processMessage(message)
        ```
        
        
        7️⃣ INTEGRATION WITH MENTAT
        =========================
        
        ```kotlin
        // In Mentat backend
        val dagPrompt = DagPlanningPrompt()
        val planningAgent = PlanningAgent(
            baseAgent = vertexAgent,
            promptBuilder = dagPrompt
        )
        
        val plan = planningAgent.processMessage(message)
        val dagDocument = AutoUpgradeRuleEngine().convertPlanToDag(plan)
        ```
        
        
        📊 CONFIGURATION OPTIONS
        =======================
        
        OutputFormat:
        - JSON: Structured JSON output
        - STRUCTURED_PLAN: Rich formatted plan
        - MARKDOWN: Documentation-ready format
        - PLAIN_TEXT: Simple text list
        
        PlanningStrategy:
        - SEQUENTIAL: Step-by-step execution
        - PARALLEL: Parallel task execution
        - HYBRID: Mix of sequential and parallel
        - MILESTONE_BASED: Organized around milestones
        - ITERATIVE: Iterative development cycles
        
        Company Profiles:
        - startup: MVP-focused, rapid iteration
        - enterprise: Compliance, governance, security
        - consulting: Client deliverables, milestones
        
        
        🎯 BEST PRACTICES
        ================
        
        1. Choose the right base agent for your needs
        2. Customize prompts for your domain/company
        3. Use appropriate output format for your use case
        4. Leverage Spring Boot auto-configuration
        5. Consider using as a tool for agent composition
        6. Provide rich context via message metadata
        7. Test with different planning strategies
        
        
        💡 TIPS
        ======
        
        - PlanningAgent is OPTIONAL - only use if you need it
        - Can be used standalone or as part of agent ecosystem
        - Highly customizable via PromptBuilder interface
        - Integrates seamlessly with Spring Boot
        - Perfect for Mentat DAG generation
        - Supports multiple AI providers (OpenAI, Anthropic, Vertex)
        
        """.trimIndent())
    }
    
    /**
     * 🚀 Quick Start Examples
     */
    fun quickStartExamples() {
        println("🌶️ QUICK START EXAMPLES")
        println("=".repeat(50))
        
        // Example 1: Basic usage
        println("1. Basic Usage:")
        println("""
        val planner = PlanningAgent(baseAgent = OpenAIAgent(apiKey))
        val plan = planner.processMessage(Message("Build a website", "user"))
        """.trimIndent())
        
        // Example 2: Tool usage
        println("\n2. As a Tool:")
        println("""
        val tool = PlanningTool(planner)
        myAgent.addTool(tool)
        val result = executeTool("planning", mapOf("goal" to "Create API"))
        """.trimIndent())
        
        // Example 3: Spring Boot
        println("\n3. Spring Boot:")
        println("""
        # application.yml
        spice.planning.enabled: true
        
        @Autowired
        private lateinit var planningAgent: PlanningAgent
        """.trimIndent())
        
        // Example 4: Custom prompt
        println("\n4. Custom Prompt:")
        println("""
        val customPrompt = CompanyPromptBuilder(
            companyTemplate = "Focus on security and compliance",
            domainSpecificRules = mapOf("web" to "Use HTTPS everywhere")
        )
        val planner = PlanningAgent(baseAgent, customPrompt)
        """.trimIndent())
    }
    
    /**
     * 🔧 Configuration Examples
     */
    fun configurationExamples() {
        println("🌶️ CONFIGURATION EXAMPLES")
        println("=".repeat(50))
        
        println("Startup Configuration:")
        println("""
        spice:
          planning:
            enabled: true
            company-profile: startup
            max-steps: 8
            planning-strategy: ITERATIVE
            output-format: STRUCTURED_PLAN
        """.trimIndent())
        
        println("\nEnterprise Configuration:")
        println("""
        spice:
          planning:
            enabled: true
            company-profile: enterprise
            max-steps: 12
            planning-strategy: MILESTONE_BASED
            include-time-estimates: true
            include-dependencies: true
        """.trimIndent())
        
        println("\nConsulting Configuration:")
        println("""
        spice:
          planning:
            enabled: true
            company-profile: consulting
            max-steps: 10
            planning-strategy: SEQUENTIAL
            output-format: MARKDOWN
        """.trimIndent())
    }
} 