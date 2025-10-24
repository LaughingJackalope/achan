package concord.dev.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MarkdownServiceTest {

    private lateinit var markdownService: MarkdownService

    @BeforeEach
    fun setup() {
        markdownService = MarkdownService()
    }

    @Test
    fun `should render basic markdown text`() {
        val markdown = "This is **bold** and this is *italic*"
        val html = markdownService.render(markdown)

        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
    }

    @Test
    fun `should render paragraphs`() {
        val markdown = """
            First paragraph

            Second paragraph
        """.trimIndent()

        val html = markdownService.render(markdown)

        assertTrue(html.contains("<p>First paragraph</p>"))
        assertTrue(html.contains("<p>Second paragraph</p>"))
    }

    @Test
    fun `should render headings`() {
        val markdown = """
            # H1 Heading
            ## H2 Heading
            ### H3 Heading
        """.trimIndent()

        val html = markdownService.render(markdown)

        assertTrue(html.contains("<h1>H1 Heading</h1>"))
        assertTrue(html.contains("<h2>H2 Heading</h2>"))
        assertTrue(html.contains("<h3>H3 Heading</h3>"))
    }

    @Test
    fun `should render unordered lists`() {
        val markdown = """
            - Item 1
            - Item 2
            - Item 3
        """.trimIndent()

        val html = markdownService.render(markdown)

        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>Item 1</li>"))
        assertTrue(html.contains("<li>Item 2</li>"))
        assertTrue(html.contains("<li>Item 3</li>"))
        assertTrue(html.contains("</ul>"))
    }

    @Test
    fun `should render ordered lists`() {
        val markdown = """
            1. First
            2. Second
            3. Third
        """.trimIndent()

        val html = markdownService.render(markdown)

        assertTrue(html.contains("<ol>"))
        assertTrue(html.contains("<li>First</li>"))
        assertTrue(html.contains("<li>Second</li>"))
        assertTrue(html.contains("<li>Third</li>"))
        assertTrue(html.contains("</ol>"))
    }

    @Test
    fun `should render inline code`() {
        val markdown = "Use the `println()` function"
        val html = markdownService.render(markdown)

        assertTrue(html.contains("<code>println()</code>"))
    }

    @Test
    fun `should render code blocks`() {
        val markdown = """
            ```kotlin
            fun main() {
                println("Hello")
            }
            ```
        """.trimIndent()

        val html = markdownService.render(markdown)

        assertTrue(html.contains("<pre>"))
        assertTrue(html.contains("<code"))
        assertTrue(html.contains("println"))
    }

    @Test
    fun `should render blockquotes`() {
        val markdown = "> This is a quote"
        val html = markdownService.render(markdown)

        assertTrue(html.contains("<blockquote>"))
        assertTrue(html.contains("This is a quote"))
        assertTrue(html.contains("</blockquote>"))
    }

    @Test
    fun `should render safe links`() {
        val markdown = "[Example](https://example.com)"
        val html = markdownService.render(markdown)

        assertTrue(html.contains("<a"))
        assertTrue(html.contains("href=\"https://example.com\""))
        assertTrue(html.contains("rel=\"nofollow\""))
        assertTrue(html.contains("Example</a>"))
    }

    @Test
    fun `should sanitize javascript URLs in links`() {
        val markdown = "[Click me](javascript:alert('XSS'))"
        val html = markdownService.render(markdown)

        // The link should be removed or sanitized
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("alert"))
    }

    @Test
    fun `should sanitize script tags`() {
        val markdown = "<script>alert('XSS')</script>"
        val html = markdownService.render(markdown)

        // Script tags should be removed
        assertFalse(html.contains("<script>"))
        assertFalse(html.contains("alert"))
    }

    @Test
    fun `should sanitize onclick attributes`() {
        val markdown = "<a href='#' onclick='alert(1)'>Click</a>"
        val html = markdownService.render(markdown)

        // onclick should be removed
        assertFalse(html.contains("onclick"))
        assertFalse(html.contains("alert"))
    }

    @Test
    fun `should sanitize img tags with onerror`() {
        val markdown = "<img src=x onerror='alert(1)'>"
        val html = markdownService.render(markdown)

        // img tags should be removed (not in whitelist)
        assertFalse(html.contains("<img"))
        assertFalse(html.contains("onerror"))
        assertFalse(html.contains("alert"))
    }

    @Test
    fun `should handle empty string`() {
        val html = markdownService.render("")
        assertEquals("", html)
    }

    @Test
    fun `should handle blank string`() {
        val html = markdownService.render("   ")
        assertEquals("", html)
    }

    @Test
    fun `should render complex nested content`() {
        val markdown = """
            # Post Title

            This is a paragraph with **bold** and *italic* text.

            ## Code Example

            Here's some code:

            ```kotlin
            fun greet(name: String) = "Hello, ${'$'}name"
            ```

            ## List

            - Point 1
            - Point 2

            Check out [this link](https://example.com) for more info.
        """.trimIndent()

        val html = markdownService.render(markdown)

        // Verify key elements are present
        assertTrue(html.contains("<h1>Post Title</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<h2>Code Example</h2>"))
        assertTrue(html.contains("<pre>"))
        assertTrue(html.contains("<code"))
        assertTrue(html.contains("<ul>"))
        assertTrue(html.contains("<li>Point 1</li>"))
        assertTrue(html.contains("<a"))
        assertTrue(html.contains("href=\"https://example.com\""))
        assertTrue(html.contains("rel=\"nofollow\""))
    }

    @Test
    fun `should sanitize data URLs`() {
        val markdown = "[Click](data:text/html,<script>alert('XSS')</script>)"
        val html = markdownService.render(markdown)

        // data: URLs should be removed
        assertFalse(html.contains("data:"))
    }

    @Test
    fun `should allow http and https URLs only`() {
        val markdown = """
            [HTTPS](https://example.com)
            [HTTP](http://example.com)
            [FTP](ftp://example.com)
            [File](file:///etc/passwd)
        """.trimIndent()

        val html = markdownService.render(markdown)

        // http and https should work
        assertTrue(html.contains("https://example.com"))
        assertTrue(html.contains("http://example.com"))

        // ftp and file should be blocked
        assertFalse(html.contains("ftp://"))
        assertFalse(html.contains("file://"))
    }
}
