package concord.dev.util

import jakarta.enterprise.context.ApplicationScoped
import java.net.IDN
import java.net.URI

@ApplicationScoped
class URLNormalizer {

    fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) {
            return null
        }

        try {
            // Regex to deconstruct URL into its main parts.
            // This is more robust against malformed parts (like spaces in path or IDN hosts) than URI/URL constructors.
            val regex = "^([a-zA-Z]+)://([^/?#]+)([^?#]*)?(\\?[^#]*)?(#.*)?".toRegex()
            val match = regex.matchEntire(url.trim()) ?: return url // Return original if not a match

            val (originalScheme, authority, pathPart, queryPart, _) = match.destructured

            // --- Parse authority into host and port ---
            val host: String
            var port: Int
            val portStart = authority.lastIndexOf(':')
            val ipv6End = authority.lastIndexOf(']')

            if (portStart > ipv6End) { // Basic check to distinguish port from IPv6 address part.
                host = authority.substring(0, portStart)
                port = authority.substring(portStart + 1).toIntOrNull() ?: -1
            } else {
                host = authority
                port = -1
            }

            // --- Apply Normalization Rules ---

            // 1. Force HTTPS
            val normalizedScheme = "https"

            // 2 & 3. Lowercase scheme/host & 8. Punycode IDN host
            val normalizedHost = IDN.toASCII(host.lowercase())

            // 4. Remove default ports
            if ((originalScheme.lowercase() == "https" && port == 443) ||
                (originalScheme.lowercase() == "http" && port == 80)) {
                port = -1
            }

            // 5. Remove trailing slash on path
            var normalizedPath = pathPart
            if (normalizedPath.length > 1 && normalizedPath.endsWith('/')) {
                normalizedPath = normalizedPath.dropLast(1)
            }

            // 6. Sort query parameters alphabetically (remove leading '?')
            val normalizedQuery = queryPart.takeIf { it.isNotEmpty() }
                ?.substring(1)
                ?.split('&')
                ?.filter { it.isNotEmpty() }
                ?.sorted()
                ?.joinToString("&")

            // 7. Remove fragment (by ignoring it)

            // Reconstruct using the URI multi-argument constructor to handle percent-encoding of path/query.
            return URI(
                normalizedScheme,
                null,
                normalizedHost,
                port,
                normalizedPath, // Pass the path as-is, constructor will encode it.
                normalizedQuery,
                null
            ).toASCIIString()

        } catch (e: Exception) {
            // Fallback for any unexpected errors during normalization.
            return url
        }
    }
}
