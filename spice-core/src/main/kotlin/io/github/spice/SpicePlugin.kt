package io.github.spice

import kotlinx.serialization.Serializable
import java.util.concurrent.ConcurrentHashMap
import java.util.ServiceLoader

/**
 * 🧩 Spice Plugin System
 * 
 * 확장 가능한 모듈형 아키텍처를 제공합니다.
 * Agent, Tool, MessageRouter 등을 플러그인으로 동적 로드할 수 있습니다.
 */

/**
 * 플러그인 메타데이터
 */
@Serializable
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val dependencies: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val category: PluginCategory = PluginCategory.GENERAL,
    val spiceVersion: String = "1.0.0"
)

/**
 * 플러그인 카테고리
 */
enum class PluginCategory {
    AGENT,          // Agent 확장
    TOOL,           // Tool 확장
    MESSAGE_ROUTER, // MessageRouter 확장
    ORCHESTRATOR,   // Orchestrator 확장
    GENERAL,        // 일반 확장
    INTEGRATION,    // 외부 서비스 통합
    CUSTOM          // 커스텀 확장
}

/**
 * 플러그인 상태
 */
enum class PluginState {
    LOADED,         // 로드됨
    ACTIVATED,      // 활성화됨
    DEACTIVATED,    // 비활성화됨
    ERROR,          // 오류 상태
    UPDATING        // 업데이트 중
}

/**
 * 플러그인 인터페이스
 */
interface SpicePlugin {
    
    /**
     * 플러그인 메타데이터
     */
    val metadata: PluginMetadata
    
    /**
     * 플러그인 초기화
     */
    fun initialize(context: PluginContext)
    
    /**
     * 플러그인 활성화
     */
    fun activate()
    
    /**
     * 플러그인 비활성화
     */
    fun deactivate()
    
    /**
     * 플러그인 정리
     */
    fun cleanup()
    
    /**
     * 플러그인 상태 확인
     */
    fun isReady(): Boolean
    
    /**
     * 플러그인 설정
     */
    fun configure(config: Map<String, Any>)
}

/**
 * 플러그인 컨텍스트
 */
data class PluginContext(
    val agentEngine: AgentEngine,
    val pluginManager: PluginManager,
    val configurationManager: ConfigurationManager,
    val logger: PluginLogger
)

/**
 * 플러그인 로거
 */
interface PluginLogger {
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, exception: Throwable? = null)
    fun debug(message: String)
}

/**
 * 간단한 플러그인 로거 구현
 */
class SimplePluginLogger(private val pluginId: String) : PluginLogger {
    override fun info(message: String) = println("🌶️ [INFO] [$pluginId] $message")
    override fun warn(message: String) = println("🌶️ [WARN] [$pluginId] $message")
    override fun error(message: String, exception: Throwable?) = println("🌶️ [ERROR] [$pluginId] $message ${exception?.message ?: ""}")
    override fun debug(message: String) = println("🌶️ [DEBUG] [$pluginId] $message")
}

/**
 * 설정 관리자
 */
interface ConfigurationManager {
    fun getConfig(pluginId: String): Map<String, Any>
    fun setConfig(pluginId: String, config: Map<String, Any>)
    fun hasConfig(pluginId: String): Boolean
}

/**
 * 간단한 설정 관리자 구현
 */
class SimpleConfigurationManager : ConfigurationManager {
    private val configs = ConcurrentHashMap<String, Map<String, Any>>()
    
    override fun getConfig(pluginId: String): Map<String, Any> {
        return configs[pluginId] ?: emptyMap()
    }
    
    override fun setConfig(pluginId: String, config: Map<String, Any>) {
        configs[pluginId] = config
    }
    
    override fun hasConfig(pluginId: String): Boolean {
        return configs.containsKey(pluginId)
    }
}

/**
 * 플러그인 정보
 */
data class PluginInfo(
    val metadata: PluginMetadata,
    val state: PluginState,
    val loadTime: Long,
    val activationTime: Long? = null,
    val errorMessage: String? = null
)

/**
 * 플러그인 매니저
 */
class PluginManager(
    private val agentEngine: AgentEngine,
    private val configurationManager: ConfigurationManager = SimpleConfigurationManager()
) {
    
    private val plugins = ConcurrentHashMap<String, SpicePlugin>()
    private val pluginStates = ConcurrentHashMap<String, PluginState>()
    private val pluginInfos = ConcurrentHashMap<String, PluginInfo>()
    private val dependencyGraph = ConcurrentHashMap<String, List<String>>()
    
    /**
     * 플러그인 로드
     */
    fun loadPlugin(plugin: SpicePlugin): Boolean {
        val pluginId = plugin.metadata.id
        
        return try {
            // 의존성 체크
            if (!checkDependencies(plugin.metadata.dependencies)) {
                throw PluginException("Dependencies not satisfied for plugin: $pluginId")
            }
            
            // 플러그인 등록
            plugins[pluginId] = plugin
            pluginStates[pluginId] = PluginState.LOADED
            
            // 플러그인 정보 저장
            pluginInfos[pluginId] = PluginInfo(
                metadata = plugin.metadata,
                state = PluginState.LOADED,
                loadTime = System.currentTimeMillis()
            )
            
            // 의존성 그래프 업데이트
            dependencyGraph[pluginId] = plugin.metadata.dependencies
            
            // 플러그인 컨텍스트 생성 및 초기화
            val context = PluginContext(
                agentEngine = agentEngine,
                pluginManager = this,
                configurationManager = configurationManager,
                logger = SimplePluginLogger(pluginId)
            )
            
            plugin.initialize(context)
            
            println("🌶️ Plugin loaded: $pluginId (${plugin.metadata.name})")
            true
            
        } catch (e: Exception) {
            pluginStates[pluginId] = PluginState.ERROR
            pluginInfos[pluginId] = PluginInfo(
                metadata = plugin.metadata,
                state = PluginState.ERROR,
                loadTime = System.currentTimeMillis(),
                errorMessage = e.message
            )
            
            println("🌶️ Failed to load plugin: $pluginId - ${e.message}")
            false
        }
    }
    
    /**
     * 플러그인 활성화
     */
    fun activatePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        return try {
            // 의존성 플러그인들이 활성화되어 있는지 확인
            val dependencies = dependencyGraph[pluginId] ?: emptyList()
            if (!dependencies.all { isPluginActive(it) }) {
                throw PluginException("Required dependencies are not active")
            }
            
            plugin.activate()
            pluginStates[pluginId] = PluginState.ACTIVATED
            
            // 플러그인 정보 업데이트
            val currentInfo = pluginInfos[pluginId]!!
            pluginInfos[pluginId] = currentInfo.copy(
                state = PluginState.ACTIVATED,
                activationTime = System.currentTimeMillis()
            )
            
            println("🌶️ Plugin activated: $pluginId")
            true
            
        } catch (e: Exception) {
            pluginStates[pluginId] = PluginState.ERROR
            println("🌶️ Failed to activate plugin: $pluginId - ${e.message}")
            false
        }
    }
    
    /**
     * 플러그인 비활성화
     */
    fun deactivatePlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        return try {
            plugin.deactivate()
            pluginStates[pluginId] = PluginState.DEACTIVATED
            
            // 플러그인 정보 업데이트
            val currentInfo = pluginInfos[pluginId]!!
            pluginInfos[pluginId] = currentInfo.copy(
                state = PluginState.DEACTIVATED,
                activationTime = null
            )
            
            println("🌶️ Plugin deactivated: $pluginId")
            true
            
        } catch (e: Exception) {
            pluginStates[pluginId] = PluginState.ERROR
            println("🌶️ Failed to deactivate plugin: $pluginId - ${e.message}")
            false
        }
    }
    
    /**
     * 플러그인 언로드
     */
    fun unloadPlugin(pluginId: String): Boolean {
        val plugin = plugins[pluginId] ?: return false
        
        return try {
            // 먼저 비활성화
            if (isPluginActive(pluginId)) {
                deactivatePlugin(pluginId)
            }
            
            plugin.cleanup()
            
            // 플러그인 제거
            plugins.remove(pluginId)
            pluginStates.remove(pluginId)
            pluginInfos.remove(pluginId)
            dependencyGraph.remove(pluginId)
            
            println("🌶️ Plugin unloaded: $pluginId")
            true
            
        } catch (e: Exception) {
            println("🌶️ Failed to unload plugin: $pluginId - ${e.message}")
            false
        }
    }
    
    /**
     * 플러그인 상태 확인
     */
    fun isPluginActive(pluginId: String): Boolean {
        return pluginStates[pluginId] == PluginState.ACTIVATED
    }
    
    /**
     * 플러그인 조회
     */
    fun getPlugin(pluginId: String): SpicePlugin? = plugins[pluginId]
    
    /**
     * 모든 플러그인 조회
     */
    fun getAllPlugins(): Map<String, SpicePlugin> = plugins.toMap()
    
    /**
     * 플러그인 정보 조회
     */
    fun getPluginInfo(pluginId: String): PluginInfo? = pluginInfos[pluginId]
    
    /**
     * 모든 플러그인 정보 조회
     */
    fun getAllPluginInfos(): Map<String, PluginInfo> = pluginInfos.toMap()
    
    /**
     * 카테고리별 플러그인 조회
     */
    fun getPluginsByCategory(category: PluginCategory): List<SpicePlugin> {
        return plugins.values.filter { it.metadata.category == category }
    }
    
    /**
     * 의존성 체크
     */
    private fun checkDependencies(dependencies: List<String>): Boolean {
        return dependencies.all { depId ->
            plugins.containsKey(depId)
        }
    }
    
    /**
     * 자동 플러그인 발견 및 로드 (ServiceLoader 기반)
     */
    fun discoverAndLoadPlugins() {
        val serviceLoader = ServiceLoader.load(SpicePlugin::class.java)
        
        serviceLoader.forEach { plugin ->
            loadPlugin(plugin)
        }
    }
    
    /**
     * 플러그인 의존성 순서로 정렬
     */
    fun getPluginsInDependencyOrder(): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val visiting = mutableSetOf<String>()
        
        fun visit(pluginId: String) {
            if (visiting.contains(pluginId)) {
                throw PluginException("Circular dependency detected: $pluginId")
            }
            
            if (!visited.contains(pluginId)) {
                visiting.add(pluginId)
                
                val dependencies = dependencyGraph[pluginId] ?: emptyList()
                dependencies.forEach { depId ->
                    if (plugins.containsKey(depId)) {
                        visit(depId)
                    }
                }
                
                visiting.remove(pluginId)
                visited.add(pluginId)
                result.add(pluginId)
            }
        }
        
        plugins.keys.forEach { pluginId ->
            visit(pluginId)
        }
        
        return result
    }
    
    /**
     * 모든 플러그인 활성화 (의존성 순서)
     */
    fun activateAllPlugins() {
        val orderedPlugins = getPluginsInDependencyOrder()
        
        orderedPlugins.forEach { pluginId ->
            if (pluginStates[pluginId] == PluginState.LOADED) {
                activatePlugin(pluginId)
            }
        }
    }
    
    /**
     * 모든 플러그인 비활성화 (역순)
     */
    fun deactivateAllPlugins() {
        val orderedPlugins = getPluginsInDependencyOrder().reversed()
        
        orderedPlugins.forEach { pluginId ->
            if (pluginStates[pluginId] == PluginState.ACTIVATED) {
                deactivatePlugin(pluginId)
            }
        }
    }
    
    /**
     * 플러그인 통계
     */
    fun getPluginStats(): PluginStats {
        val byState = pluginStates.values.groupingBy { it }.eachCount()
        val byCategory = plugins.values.groupingBy { it.metadata.category }.eachCount()
        
        return PluginStats(
            totalPlugins = plugins.size,
            stateDistribution = byState,
            categoryDistribution = byCategory,
            averageLoadTime = pluginInfos.values.map { it.loadTime }.average().takeIf { !it.isNaN() } ?: 0.0
        )
    }
}

/**
 * 플러그인 통계
 */
data class PluginStats(
    val totalPlugins: Int,
    val stateDistribution: Map<PluginState, Int>,
    val categoryDistribution: Map<PluginCategory, Int>,
    val averageLoadTime: Double
)

/**
 * 플러그인 예외
 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * === 실제 플러그인 예제들 ===
 */

/**
 * 기본 플러그인 추상 클래스
 */
abstract class BaseSpicePlugin(
    override val metadata: PluginMetadata
) : SpicePlugin {
    
    protected var context: PluginContext? = null
    protected var config: Map<String, Any> = emptyMap()
    protected var isInitialized = false
    
    override fun initialize(context: PluginContext) {
        this.context = context
        this.config = context.configurationManager.getConfig(metadata.id)
        onInitialize()
        isInitialized = true
    }
    
    override fun activate() {
        if (!isInitialized) {
            throw PluginException("Plugin not initialized: ${metadata.id}")
        }
        onActivate()
    }
    
    override fun deactivate() {
        onDeactivate()
    }
    
    override fun cleanup() {
        onCleanup()
        context = null
        config = emptyMap()
        isInitialized = false
    }
    
    override fun isReady(): Boolean = isInitialized
    
    override fun configure(config: Map<String, Any>) {
        this.config = config
        onConfigure(config)
    }
    
    /**
     * 하위 클래스에서 구현할 메서드들
     */
    protected open fun onInitialize() {}
    protected open fun onActivate() {}
    protected open fun onDeactivate() {}
    protected open fun onCleanup() {}
    protected open fun onConfigure(config: Map<String, Any>) {}
}

/**
 * 웹훅 플러그인 예제
 */
class WebhookPlugin : BaseSpicePlugin(
    metadata = PluginMetadata(
        id = "webhook-plugin",
        name = "Webhook Integration",
        version = "1.0.0",
        description = "Provides webhook integration for external services",
        author = "Spice Team",
        category = PluginCategory.INTEGRATION,
        permissions = listOf("network.http")
    )
) {
    
    private var webhookUrl: String = ""
    private var isWebhookActive = false
    
    override fun onInitialize() {
        context?.logger?.info("Initializing webhook plugin...")
        
        // 설정에서 webhook URL 가져오기
        webhookUrl = config["webhook_url"] as? String ?: ""
        
        if (webhookUrl.isEmpty()) {
            context?.logger?.warn("No webhook URL configured")
        }
    }
    
    override fun onActivate() {
        if (webhookUrl.isNotEmpty()) {
            isWebhookActive = true
            context?.logger?.info("Webhook active: $webhookUrl")
            
            // AgentEngine에 결과 리스너 등록
            // (실제 구현에서는 AgentEngine에 이벤트 시스템이 필요)
            
        } else {
            context?.logger?.warn("Cannot activate webhook: no URL configured")
        }
    }
    
    override fun onDeactivate() {
        isWebhookActive = false
        context?.logger?.info("Webhook deactivated")
    }
    
    override fun onConfigure(config: Map<String, Any>) {
        val newUrl = config["webhook_url"] as? String ?: ""
        if (newUrl != webhookUrl) {
            webhookUrl = newUrl
            context?.logger?.info("Webhook URL updated: $webhookUrl")
        }
    }
    
    /**
     * 웹훅 전송
     */
    fun sendWebhook(data: Map<String, Any>) {
        if (!isWebhookActive || webhookUrl.isEmpty()) {
            context?.logger?.warn("Webhook not active or URL not configured")
            return
        }
        
        context?.logger?.info("Sending webhook: $data")
        // 실제 HTTP 요청 구현
        // (이 예제에서는 로그만 출력)
    }
}

/**
 * 로깅 플러그인 예제
 */
class LoggingPlugin : BaseSpicePlugin(
    metadata = PluginMetadata(
        id = "logging-plugin",
        name = "Enhanced Logging",
        version = "1.0.0",
        description = "Provides enhanced logging capabilities",
        author = "Spice Team",
        category = PluginCategory.GENERAL,
        permissions = listOf("filesystem.write")
    )
) {
    
    private var logLevel: String = "INFO"
    private var logFile: String = ""
    
    override fun onInitialize() {
        logLevel = config["log_level"] as? String ?: "INFO"
        logFile = config["log_file"] as? String ?: ""
        
        context?.logger?.info("Logging plugin initialized - Level: $logLevel, File: $logFile")
    }
    
    override fun onActivate() {
        context?.logger?.info("Enhanced logging activated")
        
        // 실제 구현에서는 AgentEngine의 로깅 시스템과 연동
        // 모든 Agent 실행 로그를 캡처하여 파일에 저장
    }
    
    override fun onDeactivate() {
        context?.logger?.info("Enhanced logging deactivated")
    }
    
    /**
     * 로그 기록
     */
    fun logMessage(level: String, message: String, agentId: String? = null) {
        if (shouldLog(level)) {
            val timestamp = System.currentTimeMillis()
            val logEntry = "[$timestamp] [$level] ${agentId?.let { "[$it] " } ?: ""}$message"
            
            // 파일 또는 콘솔에 로그 출력
            if (logFile.isNotEmpty()) {
                // 파일에 기록 (실제 구현 필요)
                context?.logger?.debug("Writing to log file: $logFile")
            } else {
                println("🌶️ LOG: $logEntry")
            }
        }
    }
    
    private fun shouldLog(level: String): Boolean {
        val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
        val currentLevelIndex = levels.indexOf(logLevel)
        val messageLevelIndex = levels.indexOf(level)
        
        return messageLevelIndex >= currentLevelIndex
    }
}

/**
 * 편의 함수들
 */
fun createPluginManager(agentEngine: AgentEngine): PluginManager {
    return PluginManager(agentEngine)
}

/**
 * 플러그인 빌더 DSL
 */
fun buildPlugin(id: String, name: String, init: PluginBuilder.() -> Unit): SpicePlugin {
    val builder = PluginBuilder(id, name)
    builder.init()
    return builder.build()
}

/**
 * 플러그인 빌더
 */
class PluginBuilder(private val id: String, private val name: String) {
    var version: String = "1.0.0"
    var description: String = ""
    var author: String = ""
    var category: PluginCategory = PluginCategory.GENERAL
    var dependencies: List<String> = emptyList()
    var permissions: List<String> = emptyList()
    
    private var initHandler: (PluginContext) -> Unit = {}
    private var activateHandler: () -> Unit = {}
    private var deactivateHandler: () -> Unit = {}
    private var cleanupHandler: () -> Unit = {}
    private var configureHandler: (Map<String, Any>) -> Unit = {}
    
    fun onInitialize(handler: (PluginContext) -> Unit) {
        initHandler = handler
    }
    
    fun onActivate(handler: () -> Unit) {
        activateHandler = handler
    }
    
    fun onDeactivate(handler: () -> Unit) {
        deactivateHandler = handler
    }
    
    fun onCleanup(handler: () -> Unit) {
        cleanupHandler = handler
    }
    
    fun onConfigure(handler: (Map<String, Any>) -> Unit) {
        configureHandler = handler
    }
    
    internal fun build(): SpicePlugin {
        val metadata = PluginMetadata(
            id = id,
            name = name,
            version = version,
            description = description,
            author = author,
            dependencies = dependencies,
            permissions = permissions,
            category = category
        )
        
        return object : BaseSpicePlugin(metadata) {
            override fun onInitialize() = initHandler(context!!)
            override fun onActivate() = activateHandler()
            override fun onDeactivate() = deactivateHandler()
            override fun onCleanup() = cleanupHandler()
            override fun onConfigure(config: Map<String, Any>) = configureHandler(config)
        }
    }
} 