package com.loopsai.example

import android.content.Intent
import android.util.Log
import com.loopsai.chat.BlockAnalyticsAdapter
import com.loopsai.chat.LoopsAIChatConfig
import com.loopsai.chat.LoopsAIChatContext
import com.loopsai.chat.LoopsAnalyticsConfig
import com.loopsai.chat.LoopsEnvironment
import com.loopsai.chat.LoopsFeatureFlags

/**
 * Central place for the demo's credentials, sample data, and config building.
 *
 * Only [AGENT_ID] is needed by the SDK — everything else (the organization, the
 * agent's enabled flows, etc.) is resolved server-side from the agent id.
 */
object LoopsConfig {

    const val AGENT_ID = "j4PDx4UNSpV2tnmPoVW6"

    /**
     * The organization the agent belongs to. The SDK does not need it (the agent id
     * is sufficient); it is kept here for reference and for your own analytics/back
     * end if you want to tag events with it.
     */
    const val ORGANIZATION_ID = "Bqox4RGliaFEQAyRXnru"

    /** Sample product context used by the demo actions. */
    val sampleProduct = mapOf(
        "productCode" to "SKU-12345",
        "productName" to "Classic Cotton T-Shirt",
    )

    /** Sample user context used by the demo actions. */
    val sampleUser = mapOf(
        "userId" to "u_42",
        "firstName" to "Alex",
        "email" to "alex@example.com",
    )

    /**
     * The options the home screen collects. Kept as primitives so they can ride an
     * [Intent] to the chat screens (the real `LoopsAIChatConfig` carries analytics
     * closures and is therefore not `Parcelable`).
     */
    data class Options(
        val customEnvUrl: String? = null,
        val locale: String? = null,
        val showCloseButton: Boolean = true,
        val startFresh: Boolean = false,
        val keepAliveEnabled: Boolean = true,
        val virtualTryOn: Boolean = false,
        val searchEscalation: Boolean = false,
        val productSuggestion: Boolean = false,
        val outfitSuggestion: Boolean = false,
        val productCode: String? = sampleProduct["productCode"],
        val productName: String? = sampleProduct["productName"],
        val userId: String? = sampleUser["userId"],
        val firstName: String? = sampleUser["firstName"],
        val email: String? = sampleUser["email"],
    )

    /**
     * Build a full [LoopsAIChatConfig] from [opts]. [onAnalyticsEvent] is wired into
     * a [BlockAnalyticsAdapter] so the demo can also surface events through the
     * optional customer-adapter path (in addition to the host listener callback).
     */
    fun build(opts: Options, onAnalyticsEvent: (String) -> Unit): LoopsAIChatConfig {
        val product = buildMap {
            opts.productCode?.takeIf { it.isNotBlank() }?.let { put("productCode", it) }
            opts.productName?.takeIf { it.isNotBlank() }?.let { put("productName", it) }
        }
        val user = buildMap {
            opts.userId?.takeIf { it.isNotBlank() }?.let { put("userId", it) }
            opts.firstName?.takeIf { it.isNotBlank() }?.let { put("firstName", it) }
            opts.email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
        }

        return LoopsAIChatConfig(
            agentId = AGENT_ID,
            environment = opts.customEnvUrl?.takeIf { it.isNotBlank() }
                ?.let { LoopsEnvironment.Custom(it) }
                ?: LoopsEnvironment.Production,
            initialContext = LoopsAIChatContext(
                productContext = product.ifEmpty { null },
                userContext = user.ifEmpty { null },
            ),
            // A flag is only forwarded when ON; left off, the server-resolved agent
            // config decides. (`null` = defer to server.)
            features = LoopsFeatureFlags(
                virtualTryOnEnabled = opts.virtualTryOn.takeIf { it },
                searchEscalationEnabled = opts.searchEscalation.takeIf { it },
                productSuggestionEnabled = opts.productSuggestion.takeIf { it },
                outfitSuggestionEnabled = opts.outfitSuggestion.takeIf { it },
            ),
            analytics = LoopsAnalyticsConfig(
                // No always-on Loops sink in the demo; wire your ingest endpoint here.
                loopsSinkEndpoint = null,
                // Pluggable customer adapter — forward to Firebase/Mixpanel/Segment.
                customerAdapter = BlockAnalyticsAdapter("demo-logger") { event ->
                    Log.d("LoopsAnalytics", "${event.event} ${event.payload}")
                    onAnalyticsEvent("adapter: ${event.event}")
                },
            ),
            locale = opts.locale?.takeIf { it.isNotBlank() },
            showCloseButton = opts.showCloseButton,
            startFresh = opts.startFresh,
            keepAliveEnabled = opts.keepAliveEnabled,
        )
    }

    fun putInto(opts: Options, intent: Intent): Intent = intent.apply {
        putExtra("customEnvUrl", opts.customEnvUrl)
        putExtra("locale", opts.locale)
        putExtra("showCloseButton", opts.showCloseButton)
        putExtra("startFresh", opts.startFresh)
        putExtra("keepAliveEnabled", opts.keepAliveEnabled)
        putExtra("virtualTryOn", opts.virtualTryOn)
        putExtra("searchEscalation", opts.searchEscalation)
        putExtra("productSuggestion", opts.productSuggestion)
        putExtra("outfitSuggestion", opts.outfitSuggestion)
        putExtra("productCode", opts.productCode)
        putExtra("productName", opts.productName)
        putExtra("userId", opts.userId)
        putExtra("firstName", opts.firstName)
        putExtra("email", opts.email)
    }

    fun optionsFrom(intent: Intent): Options = Options(
        customEnvUrl = intent.getStringExtra("customEnvUrl"),
        locale = intent.getStringExtra("locale"),
        showCloseButton = intent.getBooleanExtra("showCloseButton", true),
        startFresh = intent.getBooleanExtra("startFresh", false),
        keepAliveEnabled = intent.getBooleanExtra("keepAliveEnabled", true),
        virtualTryOn = intent.getBooleanExtra("virtualTryOn", false),
        searchEscalation = intent.getBooleanExtra("searchEscalation", false),
        productSuggestion = intent.getBooleanExtra("productSuggestion", false),
        outfitSuggestion = intent.getBooleanExtra("outfitSuggestion", false),
        productCode = intent.getStringExtra("productCode"),
        productName = intent.getStringExtra("productName"),
        userId = intent.getStringExtra("userId"),
        firstName = intent.getStringExtra("firstName"),
        email = intent.getStringExtra("email"),
    )
}
