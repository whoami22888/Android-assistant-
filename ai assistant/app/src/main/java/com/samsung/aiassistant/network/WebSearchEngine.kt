package com.samsung.aiassistant.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class WebSearchEngine {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    
    suspend fun search(query: String, maxResults: Int = 10): SearchResult {
        return withContext(Dispatchers.IO) {
            try {
                val results = mutableListOf<SearchItem>()
                
                // Search using DuckDuckGo (no API key required)
                val duckDuckGoResults = searchDuckDuckGo(query, maxResults)
                results.addAll(duckDuckGoResults)
                
                SearchResult(
                    query = query,
                    items = results,
                    success = true,
                    totalResults = results.size
                )
            } catch (e: Exception) {
                SearchResult(
                    query = query,
                    items = emptyList(),
                    success = false,
                    error = e.message,
                    totalResults = 0
                )
            }
        }
    }
    
    private fun searchDuckDuckGo(query: String, maxResults: Int): List<SearchItem> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://html.duckduckgo.com/html/?q=$encodedQuery"
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36")
            .build()
        
        return try {
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return emptyList()
            
            val doc = Jsoup.parse(html)
            val results = mutableListOf<SearchItem>()
            
            doc.select(".result").take(maxResults).forEach { element ->
                val titleElement = element.select(".result__a").first()
                val snippetElement = element.select(".result__snippet").first()
                val urlElement = element.select(".result__url").first()
                
                if (titleElement != null) {
                    results.add(
                        SearchItem(
                            title = titleElement.text(),
                            snippet = snippetElement?.text() ?: "",
                            url = urlElement?.text() ?: titleElement.attr("href"),
                            source = "DuckDuckGo"
                        )
                    )
                }
            }
            
            results
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun scrapeWebpage(url: String): WebpageContent {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext WebpageContent(
                    url = url,
                    title = "",
                    content = "",
                    success = false,
                    error = "Empty response"
                )
                
                val doc = Jsoup.parse(html)
                
                // Remove script and style elements
                doc.select("script, style, nav, footer, header").remove()
                
                val title = doc.title()
                val content = doc.body().text()
                val images = doc.select("img[src]").map { it.attr("abs:src") }
                val links = doc.select("a[href]").map { it.attr("abs:href") }
                
                WebpageContent(
                    url = url,
                    title = title,
                    content = content,
                    images = images,
                    links = links,
                    success = true
                )
            } catch (e: Exception) {
                WebpageContent(
                    url = url,
                    title = "",
                    content = "",
                    success = false,
                    error = e.message
                )
            }
        }
    }
    
    suspend fun extractInformation(url: String, selector: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    .build()
                
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: return@withContext emptyList()
                
                val doc = Jsoup.parse(html)
                doc.select(selector).map { it.text() }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    suspend fun downloadFile(url: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .build()
                
                val response = client.newCall(request).execute()
                response.body?.bytes()
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class SearchResult(
    val query: String,
    val items: List<SearchItem>,
    val success: Boolean,
    val error: String? = null,
    val totalResults: Int
)

data class SearchItem(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String
)

data class WebpageContent(
    val url: String,
    val title: String,
    val content: String,
    val images: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val success: Boolean,
    val error: String? = null
)
