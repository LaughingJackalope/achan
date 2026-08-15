package concord.dev.service

import jakarta.enterprise.context.ApplicationScoped
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

/**
 * Service for rendering Markdown content to safe HTML.
 *
 * Uses CommonMark for parsing and OWASP HTML Sanitizer for XSS prevention.
 */
@ApplicationScoped
class MarkdownService {

    private val markdownParser: Parser = Parser.builder().build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder().build()
    private val sanitizer: PolicyFactory = buildSanitizer()

    /**
     * Renders markdown content to safe HTML.
     *
     * @param markdown The markdown content to render
     * @return Sanitized HTML output
     */
    fun render(markdown: String): String {
        if (markdown.isBlank()) {
            return ""
        }

        // Parse markdown to AST
        val document = markdownParser.parse(markdown)

        // Render to HTML
        val rawHtml = htmlRenderer.render(document)

        // Sanitize HTML to prevent XSS
        return sanitizer.sanitize(rawHtml)
    }

    private fun buildSanitizer(): PolicyFactory {
        return HtmlPolicyBuilder()
            // Allow basic text formatting
            .allowElements(
                "p", "br", "strong", "em", "code", "pre",
                "h1", "h2", "h3", "h4", "h5", "h6",
                "blockquote"
            )
            // Allow lists
            .allowElements("ul", "ol", "li")
            // Allow links with safe protocols
            .allowElements("a")
            .allowAttributes("href").onElements("a")
            .allowStandardUrlProtocols()
            .requireRelNofollowOnLinks()
            // Allow code blocks with language class
            .allowAttributes("class")
            .matching(java.util.regex.Pattern.compile("language-\\w+"))
            .onElements("code")
            .toFactory()
    }
}
