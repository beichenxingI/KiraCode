package com.kirakira.kiracode.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * URL 安全策略测试（W6/W8）：
 * 拒绝：file/content/javascript/data、回环、link-local、私网；
 * 接受：公网 http(s) 地址。
 */
class UrlSafetyTest {

    @Test
    fun `rejects dangerous schemes`() {
        assertFalse(UrlSafety.isAllowed("file:///etc/passwd"))
        assertFalse(UrlSafety.isAllowed("content://media/images"))
        assertFalse(UrlSafety.isAllowed("javascript:alert(1)"))
        assertFalse(UrlSafety.isAllowed("data:text/html,hello"))
        assertFalse(UrlSafety.isAllowed("ftp://example.com/file"))
    }

    @Test
    fun `rejects loopback and localhost`() {
        assertFalse(UrlSafety.isAllowed("http://127.0.0.1/x"))
        assertFalse(UrlSafety.isAllowed("http://localhost/x"))
        assertFalse(UrlSafety.isAllowed("http://127.1.2.3/x"))
        assertFalse(UrlSafety.isAllowed("https://localhost:8080/"))
        assertFalse(UrlSafety.isAllowed("http://[::1]/x"))
        assertFalse(UrlSafety.isAllowed("http://0.0.0.0/x"))
    }

    @Test
    fun `rejects link-local and private networks`() {
        assertFalse(UrlSafety.isAllowed("http://192.168.1.10/x"))
        assertFalse(UrlSafety.isAllowed("http://10.0.0.5/x"))
        assertFalse(UrlSafety.isAllowed("http://172.16.0.1/x"))
        assertFalse(UrlSafety.isAllowed("http://172.31.255.255/x"))
        assertFalse(UrlSafety.isAllowed("http://169.254.1.1/x"))
        assertFalse(UrlSafety.isAllowed("http://mybox.local/x"))
        assertFalse(UrlSafety.isAllowed("http://svc.internal/x"))
    }

    @Test
    fun `accepts public https and http urls`() {
        assertTrue(UrlSafety.isAllowed("https://github.com/anthropics"))
        assertTrue(UrlSafety.isAllowed("https://www.example.com/page?q=1"))
        assertTrue(UrlSafety.isAllowed("http://example.com/"))
    }

    @Test
    fun `rejects blank and malformed urls`() {
        assertFalse(UrlSafety.isAllowed(""))
        assertFalse(UrlSafety.isAllowed("   "))
        assertFalse(UrlSafety.isAllowed("not a url"))
        assertFalse(UrlSafety.isAllowed("example.com/no-scheme"))
    }
}
