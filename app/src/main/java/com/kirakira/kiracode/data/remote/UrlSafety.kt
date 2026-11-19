package com.kirakira.kiracode.data.remote

import java.net.InetAddress
import java.net.URI

/**
 * 网页抓取 URL 安全策略（web_search 与 fetch_webpage 共用）。
 *
 * 允许：http:// 与 https:// 的公网地址。
 * 拒绝：
 * - 非 http(s) scheme（file:// content:// javascript: data: 等）
 * - 本地回环 / link-local / 私有内网 / 明显本地服务地址（仅对 IP 字面量判定，
 *   域名不做 DNS 预解析——解析失败交给 HTTP 层，也避免测试/离线环境误拒）
 */
object UrlSafety {

    private val IPV4_PATTERN = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isAllowed(rawUrl: String): Boolean {
        val url = rawUrl.trim()
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.startsWith("file:") || lower.startsWith("content:") ||
            lower.startsWith("javascript:") || lower.startsWith("data:") ||
            lower.startsWith("ftp:") || lower.startsWith("ws:") || lower.startsWith("wss:")
        ) {
            return false
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return runCatching {
            val uri = URI(url)
            val host = uri.host?.lowercase()?.removeSuffix(".") ?: return false
            if (host.isEmpty()) return false
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
                host.endsWith(".internal") || host.endsWith(".lan")
            ) {
                return false
            }
            if (isPrivateOrLocalIpLiteral(host)) return false
            true
        }.getOrDefault(false)
    }

    /** 仅对 IP 字面量做私网/回环/link-local 判定（不触发 DNS 查询） */
    private fun isPrivateOrLocalIpLiteral(host: String): Boolean {
        val isIpv4 = IPV4_PATTERN.matches(host)
        val isIpv6 = host.contains(":")
        if (!isIpv4 && !isIpv6) return false
        return runCatching {
            val addr = InetAddress.getByName(host) // 纯字面量，无 DNS 查询
            addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                addr.isSiteLocalAddress || addr.isAnyLocalAddress ||
                addr.isMulticastAddress
        }.getOrDefault(true) // 解析失败的 IP 字面量按保守策略拒绝
    }
}
