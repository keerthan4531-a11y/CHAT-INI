package com.chatapp.aiengine.network

import com.chatapp.aiengine.model.LaneUsage
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.ProxyType
import com.chatapp.aiengine.model.RateLimitPolicy
import com.chatapp.aiengine.model.RotationStrategy
import com.chatapp.aiengine.model.UsageSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.net.Proxy
import okhttp3.Request
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * A small, dependency-light proxy pool.
 *
 * Each [ProxyEndpoint] is a lane with its own daily message budget. The pool
 * picks a healthy lane (least-used / round-robin / random / fastest), consumes
 * one message from it, and hands back an OkHttp client pre-wired with proxy
 * authentication. This is what lets one user scale past the 50 msg/day per-IP
 * limit without ever changing the product policy.
 */
class ProxyPool(
    initialProxies: List<ProxyEndpoint> = emptyList(),
    private val rateLimiter: RateLimiter = RateLimiter(),
    private val policy: RateLimitPolicy = rateLimiter.policy(),
    private val healthCheckUrl: String = "https://api.github.com/zen"
) {
    data class LaneSelection(
        val lane: LaneUsage,
        val proxy: ProxyEndpoint?,
        val snapshot: UsageSnapshot
    )

    private val _proxies = MutableStateFlow(initialProxies)
    val proxies: StateFlow<List<ProxyEndpoint>> = _proxies.asStateFlow()

    private val _strategy = MutableStateFlow(RotationStrategy.LEAST_USED)
    val strategy: StateFlow<RotationStrategy> = _strategy.asStateFlow()

    private val _lastRotated = MutableStateFlow<ProxyEndpoint?>(null)
    val lastRotated: StateFlow<ProxyEndpoint?> = _lastRotated.asStateFlow()

    private val roundRobin = AtomicInteger(0)
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    fun setStrategy(strategy: RotationStrategy) {
        _strategy.value = strategy
    }

    fun setProxies(proxies: List<ProxyEndpoint>) {
        _proxies.value = proxies
        val ids = proxies.map { it.id }.toSet()
        clients.keys.toList().forEach { id -> if (id !in ids) clients.remove(id) }
    }

    fun addProxy(proxy: ProxyEndpoint) {
        _proxies.value = _proxies.value + proxy
    }

    fun removeProxy(id: String) {
        _proxies.value = _proxies.value.filterNot { it.id == id }
        clients.remove(id)
    }

    fun updateProxy(id: String, transform: (ProxyEndpoint) -> ProxyEndpoint) {
        _proxies.value = _proxies.value.map { if (it.id == id) transform(it) else it }
    }

    fun healthyProxies(): List<ProxyEndpoint> = _proxies.value.filter { it.healthy }

    fun totalCapacity(): Int = policy.perIpDailyLimit + healthyProxies().size * policy.perProxyDailyLimit

    fun snapshot(ipKey: String): UsageSnapshot = rateLimiter.snapshot(ipKey, _proxies.value)

    fun remainingFor(ipKey: String): Int = snapshot(ipKey).remaining

    /**
     * Picks the next lane and consumes one message from it.
     * Returns null when every lane is exhausted — callers should emit
     * [com.chatapp.aiengine.model.StreamEvent.RateLimitReached].
     */
    fun acquire(ipKey: String): LaneSelection? {
        val healthy = healthyProxies()
        val candidates = buildList {
            add(DirectLane)
            healthy.forEach { add(it) }
        }
        val available = candidates.filter { candidate ->
            val limit = if (candidate === DirectLane) policy.perIpDailyLimit else policy.perProxyDailyLimit
            rateLimiter.canUse(candidate.id, limit, candidate === DirectLane, ipKey)
        }
        if (available.isEmpty()) return null

        val ordered = when (_strategy.value) {
            RotationStrategy.LEAST_USED -> available.sortedBy { candidate ->
                val limit = if (candidate === DirectLane) policy.perIpDailyLimit else policy.perProxyDailyLimit
                val used = if (candidate === DirectLane) rateLimiter.ipUsed(ipKey) else rateLimiter.proxyUsed(candidate.id)
                used.toFloat() / limit
            }

            RotationStrategy.ROUND_ROBIN -> {
                val start = roundRobin.getAndUpdate { it + 1 } % available.size
                available.subList(start, available.size) + available.subList(0, start)
            }

            RotationStrategy.RANDOM -> available.shuffled()
            RotationStrategy.FASTEST -> available.sortedBy { if (it === DirectLane) 0 else it.latencyMs }
        }

        for (candidate in ordered) {
            val isDirect = candidate === DirectLane
            val limit = if (isDirect) policy.perIpDailyLimit else policy.perProxyDailyLimit
            val result = rateLimiter.consume(
                ipKey = ipKey,
                laneId = candidate.id,
                laneLimit = limit,
                isDirectLane = isDirect,
                proxies = _proxies.value
            )
            if (result is com.chatapp.aiengine.model.RateLimitResult.Allowed) {
                if (!isDirect) {
                    _lastRotated.value = candidate
                    updateProxy(candidate.id) { it.copy(usedToday = it.usedToday + 1) }
                }
                return LaneSelection(lane = result.lane, proxy = if (isDirect) null else candidate, snapshot = result.snapshot)
            }
        }
        return null
    }

    /** Non-consuming preview used by the UI ("next lane: ..."). */
    fun previewLane(ipKey: String): LaneUsage? {
        val healthy = healthyProxies()
        val candidates = listOf<ProxyEndpoint?>(null) + healthy
        val available = candidates.filter { candidate ->
            val limit = if (candidate == null) policy.perIpDailyLimit else policy.perProxyDailyLimit
            rateLimiter.canUse(candidate?.id ?: "direct", limit, candidate == null, ipKey)
        }
        if (available.isEmpty()) return null
        val chosen = when (_strategy.value) {
            RotationStrategy.LEAST_USED -> available.minByOrNull { candidate ->
                if (candidate == null) rateLimiter.ipUsed(ipKey).toFloat() / policy.perIpDailyLimit
                else rateLimiter.proxyUsed(candidate.id).toFloat() / policy.perProxyDailyLimit
            }

            RotationStrategy.ROUND_ROBIN -> available[roundRobin.get() % available.size]
            RotationStrategy.RANDOM -> available.random()
            RotationStrategy.FASTEST -> available.minByOrNull { it?.latencyMs ?: 0 }
        }
        return snapshot(ipKey).lanes.firstOrNull { it.id == (chosen?.id ?: "direct") }
    }

    /**
     * Applies proxy + proxy auth to an existing client builder. Handy for
     * providers that need to keep their own cookie jar / timeouts.
     */
    fun applyProxy(builder: OkHttpClient.Builder, proxy: ProxyEndpoint?): OkHttpClient.Builder {
        if (proxy == null || proxy.simulated || proxy.host.isBlank() || proxy.host.endsWith(".pawpal.dev")) return builder
        val javaProxy = when (proxy.type) {
            ProxyType.HTTP, ProxyType.HTTPS -> Proxy(Proxy.Type.HTTP, InetSocketAddress(proxy.host, proxy.port))
            ProxyType.SOCKS5 -> Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxy.host, proxy.port))
        }
        builder.proxy(javaProxy)
        if (proxy.username.isNotEmpty() && proxy.password.isNotEmpty()) {
            builder.proxyAuthenticator(
                Authenticator { _, response ->
                    val credential = Credentials.basic(proxy.username, proxy.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            )
        }
        return builder
    }

    /**
     * Builds an OkHttp client for a lane. Clients are cached per proxy so
     * connection pools stay warm while rotating.
     */
    fun httpClientFor(proxy: ProxyEndpoint?): OkHttpClient {
        if (proxy == null || proxy.simulated || proxy.host.isBlank() || proxy.host.endsWith(".pawpal.dev")) {
            return HttpClientProvider.defaultClient
        }
        return clients.getOrPut(proxy.id) {
            val builder = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(4, 5, TimeUnit.MINUTES))
            applyProxy(builder, proxy).build()
        }
    }

    /**
     * Probes every proxy through the proxy itself. Safe to call from a
     * coroutine — it hops to Dispatchers.IO internally.
     */
    suspend fun checkAllHealth(): List<ProxyEndpoint> = withContext(Dispatchers.IO) {
        val checked = _proxies.value.map { proxy ->
            if (proxy.simulated) {
                // Demo lanes are simulated; keep them healthy so the pool demo works offline.
                return@map proxy.copy(healthy = true)
            }
            val started = System.currentTimeMillis()
            val healthy = try {
                val client = httpClientFor(proxy)
                val request = Request.Builder().url(healthCheckUrl).head().build()
                client.newCall(request).execute().use { response ->
                    response.isSuccessful || response.code in 200..399
                }
            } catch (_: Exception) {
                false
            }
            val latency = (System.currentTimeMillis() - started).toInt()
            proxy.copy(healthy = healthy, latencyMs = latency)
        }
        _proxies.value = checked
        checked
    }

    fun resetUsage() {
        rateLimiter.resetAll()
        _proxies.value = _proxies.value.map { it.copy(usedToday = 0) }
    }

    companion object {
        /** Sentinel for the user's own IP lane (not a real proxy). */
        val DirectLane = ProxyEndpoint(
            id = "direct",
            label = "Your IP",
            host = "",
            port = 0,
            region = "direct",
            healthy = true
        )

        fun demoPool(): List<ProxyEndpoint> = listOf(
            ProxyEndpoint("p-mum", "Mumbai · IN", "in-mum-01.pawpal.dev", 8080, region = "IN", latencyMs = 82, simulated = true),
            ProxyEndpoint("p-che", "Chennai · IN", "in-che-02.pawpal.dev", 8080, region = "IN", latencyMs = 64, simulated = true),
            ProxyEndpoint("p-sin", "Singapore · SG", "sg-sin-01.pawpal.dev", 8080, region = "SG", latencyMs = 48, simulated = true),
            ProxyEndpoint("p-fra", "Frankfurt · DE", "de-fra-03.pawpal.dev", 8080, region = "DE", latencyMs = 141, simulated = true),
            ProxyEndpoint("p-va", "Virginia · US", "us-va-04.pawpal.dev", 8080, region = "US", latencyMs = 188, healthy = false, simulated = true),
            ProxyEndpoint("p-tyo", "Tokyo · JP", "jp-tyo-05.pawpal.dev", 8080, region = "JP", latencyMs = 96, simulated = true)
        )
    }
}
