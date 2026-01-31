package concord.dev.config

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import io.quarkus.logging.Log

/**
 * Configuration for this AChan instance in a federated deployment.
 *
 * Each instance has:
 * - Unique ID (e.g., "instance-A", "instance-B")
 * - Region/location identifier
 * - Capability tags (which agents it runs)
 *
 * This enables:
 * 1. Event attribution (know which instance created each event)
 * 2. Instance-specific agent deployment (specialization)
 * 3. Health monitoring and federation tracking
 */
@ApplicationScoped
class InstanceConfig(
    @ConfigProperty(name = "instance.id", defaultValue = "instance-local")
    val instanceId: String,

    @ConfigProperty(name = "instance.region", defaultValue = "us-west-1")
    val region: String,

    @ConfigProperty(name = "instance.capabilities", defaultValue = "context,llm,summary")
    val capabilitiesStr: String
) {

    val capabilities: Set<String> by lazy {
        capabilitiesStr.split(",").map { it.trim() }.toSet()
    }

    fun onStart(@Observes ev: StartupEvent) {
        Log.infof("=== AChan Instance Configuration ===")
        Log.infof("Instance ID: %s", instanceId)
        Log.infof("Region: %s", region)
        Log.infof("Capabilities: %s", capabilities)
        Log.infof("====================================")
    }

    /**
     * Check if this instance has a specific capability enabled.
     */
    fun hasCapability(capability: String): Boolean {
        return capabilities.contains(capability)
    }

    /**
     * Get instance information for health checks and monitoring.
     */
    fun getInstanceInfo(): Map<String, Any> {
        return mapOf(
            "instanceId" to instanceId,
            "region" to region,
            "capabilities" to capabilities,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Generate a qualified agent ID that includes instance information.
     * Format: "{instanceId}:{agentName}"
     *
     * This allows tracking which instance's agent created an event.
     */
    fun qualifiedAgentId(agentName: String): String {
        return "$instanceId:$agentName"
    }
}
