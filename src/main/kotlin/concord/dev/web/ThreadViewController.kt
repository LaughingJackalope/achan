package concord.dev.web

import concord.dev.domain.ThreadId
import concord.dev.service.PageContentService
import concord.dev.service.PostService
import concord.dev.service.ThreadService
import jakarta.ws.rs.*
import jakarta.ws.rs.core.MediaType
import java.util.UUID

@Path("/achan")
@Produces(MediaType.TEXT_HTML)
class ThreadViewController(
    private val threadService: ThreadService,
    private val pageContentService: PageContentService,
    private val postService: PostService
) {

    /**
     * Render thread view by thread ID (for testing)
     * GET /achan/t/{threadId}?theme=dark
     */
    @GET
    @Path("/t/{threadId}")
    fun viewThreadById(
        @PathParam("threadId") threadIdString: String,
        @QueryParam("theme") theme: String?
    ): String {
        val threadId = ThreadId(UUID.fromString(threadIdString))
        val thread = threadService.getThread(threadId)
            ?: return renderError("Thread not found", theme)

        val content = pageContentService.getContent(ThreadId(thread.id))
        val posts = postService.getPosts(ThreadId(thread.id), size = 100, page = 0)

        return renderThread(thread, content, posts, theme)
    }

    /**
     * Render thread view by URL
     * GET /achan?url=https://...&theme=dark
     */
    @GET
    fun viewThreadByUrl(
        @QueryParam("url") url: String?,
        @QueryParam("theme") theme: String?
    ): String {
        if (url == null) {
            return renderError("URL parameter required", theme)
        }

        val thread = threadService.getThreadByUrl(url)
            ?: return renderError("Thread not found for URL: $url", theme)

        val content = pageContentService.getContent(ThreadId(thread.id))
        val posts = postService.getPosts(ThreadId(thread.id), size = 100, page = 0)

        return renderThread(thread, content, posts, theme)
    }

    private fun renderThread(thread: Any, content: Any?, posts: List<Any>, theme: String?): String {
        val themeLink = if (theme != null) """<link rel="stylesheet" href="/themes/${theme}.css">""" else ""

        // Use reflection to get properties (simplified for now)
        val threadProps = thread::class.java.declaredFields.associate {
            it.isAccessible = true
            it.name to it.get(thread)
        }

        val contentProps = content?.let { c ->
            c::class.java.declaredFields.associate {
                it.isAccessible = true
                it.name to it.get(c)
            }
        }

        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${threadProps["url"]} - AChan</title>
    <link rel="stylesheet" href="/css/base.css">
    $themeLink
</head>
<body>
    <div class="url">
        <a href="${threadProps["url"]}" rel="nofollow">${threadProps["url"]}</a>
    </div>

    <hr class="thick">

    ${if (contentProps != null) """
    <header class="thread-header">
        <h1 class="thread-title">${contentProps["title"] ?: threadProps["url"]}</h1>
        ${if (contentProps["description"] != null) """
        <p class="thread-description">${contentProps["description"]}</p>
        """ else ""}
    </header>

    ${if (contentProps["articleText"] != null) """
    <article class="article-content">
        <p>${contentProps["articleText"]}</p>
    </article>
    """ else ""}
    """ else ""}

    <div class="metadata">
        ${threadProps["postCount"]} ${if (threadProps["postCount"] == 1) "post" else "posts"}
        ${if (contentProps != null) "· crawled ${contentProps["fetchedAt"]}" else ""}
        · status: ${threadProps["crawlStatus"]}
    </div>

    <hr>

    <footer class="metadata">
        <p>
            <a href="/">Home</a>
            ·
            Themes:
            <a href="?theme=">Default</a> |
            <a href="?theme=dark">Dark</a> |
            <a href="?theme=sepia">Sepia</a> |
            <a href="?theme=high-contrast">High Contrast</a>
        </p>
    </footer>
</body>
</html>"""
    }

    private fun renderError(message: String, theme: String?): String {
        val themeLink = if (theme != null) """<link rel="stylesheet" href="/themes/${theme}.css">""" else ""
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Error - AChan</title>
    <link rel="stylesheet" href="/css/base.css">
    $themeLink
</head>
<body>
    <h1>Error</h1>
    <p>$message</p>
</body>
</html>"""
    }
}