package com.liquidtv.app.data.audio

import java.net.InetAddress
import java.net.URI

/**
 * Validates external audio M3U and stream URLs to protect against SSRF
 * targeting loopback, link-local, multicast, or cloud metadata endpoints,
 * while preserving legitimate local/RFC1918 IPTV boxes.
 */
object AudioNetworkSecurityValidator {
    private val BLOCKED_HOSTNAMES = setOf(
        "localhost",
        "metadata.google.internal",
        "instance-data",
        "169.254.169.254",
        "169.254.170.2",
        "100.100.100.200",
        "168.63.129.16",
        "fd00:ec2::254",
    )

    fun assertSafeUrl(
        urlString: String?,
        dnsResolver: (String) -> Array<InetAddress> = { host -> InetAddress.getAllByName(host) },
    ) {
        require(!urlString.isNullOrBlank()) { "الرابط فارغ" }
        val trimmed = urlString.trim()
        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("عنوان الرابط غير صالح: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase()
        require(scheme == "http" || scheme == "https") {
            "بروتوكول الرابط غير مسموح (يجب أن يكون http أو https)"
        }

        val host = uri.host?.removePrefix("[")?.removeSuffix("]")?.lowercase()
        require(!host.isNullOrBlank()) { "عنوان المضيف في الرابط فارغ" }

        if (BLOCKED_HOSTNAMES.any { host == it || host.endsWith(".$it") } ||
            host.startsWith("127.") ||
            host == "::1" ||
            host == "0:0:0:0:0:0:0:1" ||
            host == "0.0.0.0" ||
            host.startsWith("169.254.")
        ) {
            throw SecurityException("تم رفض الاتصال بعنوان محلي أو محظور: $host")
        }

        try {
            val addresses = dnsResolver(host)
            require(addresses.isNotEmpty()) { "تعذّر تحديد عنوان الخادم" }
            addresses.firstOrNull { isBlockedAddress(it) }?.let { address ->
                throw SecurityException("تم حظر الاتصال بالعنوان: ${address.hostAddress}")
            }
        } catch (e: SecurityException) {
            throw e
        } catch (_: Exception) {
            // Preserve compatibility with IPTV hostnames that resolve only at connect time.
        }
    }

    fun isBlockedAddress(address: InetAddress): Boolean {
        if (
            address.isLoopbackAddress ||
            address.isAnyLocalAddress ||
            address.isLinkLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        if (bytes.size == 4) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 0) return true
            if (
                (b0 == 100 && b1 == 100 && (bytes[2].toInt() and 0xFF) == 100 && (bytes[3].toInt() and 0xFF) == 200) ||
                (b0 == 168 && b1 == 63 && (bytes[2].toInt() and 0xFF) == 129 && (bytes[3].toInt() and 0xFF) == 16)
            ) {
                return true
            }
            if (b0 in 224..239 || (b0 == 255 && b1 == 255)) return true
        } else if (bytes.size == 16) {
            val awsMetadataV6 = byteArrayOf(
                0xFD.toByte(), 0x00, 0x0E, 0xC2.toByte(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x54,
            )
            if (bytes.contentEquals(awsMetadataV6)) return true
        }
        return false
    }
}
