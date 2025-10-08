package concord.dev.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class URLNormalizerTest {

    private val normalizer = URLNormalizer()

    @Test
    fun `test basic normalization`() {
        assertEquals("https://example.com/path", normalizer.normalize("http://Example.com:80/path/"))
        assertEquals("https://example.com/path", normalizer.normalize("https://Example.com:443/path/"))
    }

    @Test
    fun `test query parameter sorting`() {
        assertEquals("https://example.com/path?a=1&b=2", normalizer.normalize("https://example.com/path?b=2&a=1"))
    }

    @Test
    fun `test fragment removal`() {
        assertEquals("https://example.com/path", normalizer.normalize("https://example.com/path#section"))
    }

    @Test
    fun `test www vs non-www`() {
        assertEquals("https://www.example.com", normalizer.normalize("https://www.example.com"))
        assertEquals("https://example.com", normalizer.normalize("https://example.com"))
    }

    @Test
    fun `test url shorteners`() {
        assertEquals("https://bit.ly/2sje3f", normalizer.normalize("https://bit.ly/2sje3f"))
    }

    @Test
    fun `test punycode domains`() {
        assertEquals("https://xn--mller-kva.com", normalizer.normalize("https://müller.com"))
    }

    @Test
    fun `test invalid urls`() {
        assertEquals("not a url", normalizer.normalize("not a url"))
        assertEquals("http:///a", normalizer.normalize("http:///a"))
    }

    @Test
    fun `test empty and blank urls`() {
        assertEquals(null, normalizer.normalize(""))
        assertEquals(null, normalizer.normalize("   "))
        assertEquals(null, normalizer.normalize(null))
    }
    
    @Test
    fun `test special characters`() {
        assertEquals("https://example.com/a%20b", normalizer.normalize("https://example.com/a b"))
    }
}
