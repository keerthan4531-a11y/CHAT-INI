package com.chatapp.aiengine.network

import com.chatapp.aiengine.model.LaneUsage
import com.chatapp.aiengine.model.ProxyEndpoint
import com.chatapp.aiengine.model.RateLimitPolicy
import com.chatapp.aiengine.model.RateLimitResult
import com.chatapp.aiengine.model.UsageSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-IP daily message budget with proxy lanes.
 *
 * Policy (product requirement):
 * - There is **no global fixed daily cap**.
 * - Every IP gets [RateLimitPolicy.perIpDailyLimit] messages per rolling window.
 * - Every healthy proxy adds another [RateLimitPolicy.perProxyDailyLimit] lane.
 * - Rotating the proxy pool is the supported way to increase capacity.
 *
 * The limiter is intentionally dependency-free and thread-safe so it can sit in
 * a Ktor/OkHttp gateway, an Android service, or a plain JVM unit test.
 */
class RateLimiter(
    private val policy: RateLimitPolicy = RateLimitPolicy()
) {
    private class Bucket {
        var windowStart: Long = System.currentTimeMillis()
        var used: Int = 0
    }

    private val ipBuckets = ConcurrentHashMap<String, Bucket>()
    private val proxyBuckets = ConcurrentHashMap<String, Bucket>()
    private val lastResetAt = AtomicLong(0L)

    private fun windowLengthMs(): Long = policy.windowHours * 3_600_000L

    private fun bucketFor(map: ConcurrentHashMap<String, Bucket>, key: String): Bucket {
        val now = System.currentTimeMillis()
        return map.compute(key) { _, existing ->
            val bucket = existing ?: Bucket()
            if (now - bucket.windowStart >= windowLengthMs()) {
                bucket.windowStart = now
                bucket.used = 0
            }
            bucket
        }!!
    }

    private fun peek(map: ConcurrentHashMap<String, Bucket>, key: String): Bucket {
        val now = System.currentTimeMillis()
        val bucket = map[key] ?: Bucket().also { map[key] = it }
        if (now - bucket.windowStart >= windowLengthMs()) {
            bucket.windowStart = now
            bucket.used = 0
        }
        return bucket
    }

    fun ipUsed(ipKey: String): Int = peek(ipBuckets, ipKey).used

    fun proxyUsed(proxyId: String): Int = peek(proxyBuckets, proxyId).used

    fun resetAt(): Long {
        val bucket = ipBuckets.values.firstOrNull() ?: return System.currentTimeMillis() + windowLengthMs()
        return bucket.windowStart + windowLengthMs()
    }

    /**
     * Tries to consume one message from a specific lane.
     * Returns the updated snapshot regardless of success so the UI can render
     * an accurate meter even when the request is rejected.
     */
    fun consume(
        ipKey: String,
        laneId: String,
        laneLimit: Int,
        isDirectLane: Boolean,
        proxies: List<ProxyEndpoint>
    ): RateLimitResult {
        val bucket = if (isDirectLane) bucketFor(ipBuckets, ipKey) else bucketFor(proxyBuckets, laneId)
        synchronized(bucket) {
            if (bucket.used >= laneLimit) {
                return Rejected(snapshot(ipKey, proxies), bucket.windowStart + windowLengthMs())
            }
            bucket.used += 1
        }
        lastResetAt.set(bucket.windowStart + windowLengthMs())
        return Allowed(
            lane = LaneUsage(
                id = laneId,
                label = if (isDirectLane) "Your IP" else proxies.firstOrNull { it.id == laneId }?.label ?: laneId,
                emoji = if (isDirectLane) "🏠" else "🛰️",
                used = bucket.used,
                limit = laneLimit,
                healthy = true,
                latencyMs = if (isDirectLane) 0 else proxies.firstOrNull { it.id == laneId }?.latencyMs ?: 0,
                region = if (isDirectLane) "direct" else proxies.firstOrNull { it.id == laneId }?.region ?: "",
                isDirect = isDirectLane
            ),
            snapshot = snapshot(ipKey, proxies)
        )
    }

    /** Checks a lane without consuming. Used by the proxy picker. */
    fun canUse(laneId: String, limit: Int, isDirectLane: Boolean, ipKey: String): Boolean {
        val bucket = if (isDirectLane) peek(ipBuckets, ipKey) else peek(proxyBuckets, laneId)
        return bucket.used < limit
    }

    fun snapshot(ipKey: String, proxies: List<ProxyEndpoint>): UsageSnapshot {
        val ipBucket = peek(ipBuckets, ipKey)
        val healthyProxies = proxies.filter { it.healthy }
        val proxyLimit = healthyProxies.size * policy.perProxyDailyLimit
        val proxyUsed = healthyProxies.sumOf { peek(proxyBuckets, it.id).used }
        val lanes = buildList {
            add(
                LaneUsage(
                    id = "direct",
                    label = "Your IP",
                    emoji = "🏠",
                    used = ipBucket.used,
                    limit = policy.perIpDailyLimit,
                    healthy = true,
                    latencyMs = 0,
                    region = "direct",
                    isDirect = true
                )
            )
            proxies.forEach { proxy ->
                add(
                    LaneUsage(
                        id = proxy.id,
                        label = proxy.label,
                        emoji = "🛰️",
                        used = peek(proxyBuckets, proxy.id).used,
                        limit = policy.perProxyDailyLimit,
                        healthy = proxy.healthy,
                        latencyMs = proxy.latencyMs,
                        region = proxy.region
                    )
                )
            }
        }
        return UsageSnapshot(
            ipKey = ipKey,
            ipUsed = ipBucket.used,
            ipLimit = policy.perIpDailyLimit,
            proxyUsed = proxyUsed,
            proxyLimit = proxyLimit,
            healthyProxies = healthyProxies.size,
            totalCapacity = policy.perIpDailyLimit + proxyLimit,
            resetAt = ipBucket.windowStart + windowLengthMs(),
            lanes = lanes
        )
    }

    /** Demo helper: clears every lane for this key. */
    fun resetAll() {
        ipBuckets.clear()
        proxyBuckets.clear()
        lastResetAt.set(0L)
    }

    fun policy(): RateLimitPolicy = policy
}
