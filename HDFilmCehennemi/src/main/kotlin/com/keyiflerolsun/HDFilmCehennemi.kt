// ! https://github.com/hexated/cloudstream-extensions-hexated/blob/master/Hdfilmcehennemi/src/main/kotlin/com/hexated/Hdfilmcehennemi.kt

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.base64DecodeArray
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.lang.Math.floorMod

class HDFilmCehennemi : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi.nl"
    override var name = "HDFilmCehennemi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // ! Cf bypass
    override var sequentialMainPage =
        true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay = 200L
    override var sequentialMainPageScrollDelay = 200L

    // ! cf bypass v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.select("title").text() == "Just a moment..." || doc.select("title")
                    .text() == "Bir dakika lütfen..."
            ) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }


    override val mainPage = mainPageOf(
        "${mainUrl}/load/page/sayfano/home/" to "Yeni Eklenen Filmler",
        "${mainUrl}/load/page/sayfano/categories/nette-ilk-filmler/" to "Nette İlk Filmler",
        "${mainUrl}/load/page/sayfano/home-series/" to "Yeni Eklenen Diziler",
        "${mainUrl}/load/page/sayfano/categories/tavsiye-filmler-izle2/" to "Tavsiye Filmler",
        "${mainUrl}/load/page/sayfano/imdb7/" to "IMDB 7+ Filmler",
        "${mainUrl}/load/page/sayfano/mostCommented/" to "En Çok Yorumlananlar",
        "${mainUrl}/load/page/sayfano/mostLiked/" to "En Çok Beğenilenler",
        "${mainUrl}/load/page/sayfano/genres/aile-filmleri-izleyin-6/" to "Aile Filmleri",
        "${mainUrl}/load/page/sayfano/genres/aksiyon-filmleri-izleyin-5/" to "Aksiyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/animasyon-filmlerini-izleyin-5/" to "Animasyon Filmleri",
        "${mainUrl}/load/page/sayfano/genres/belgesel-filmlerini-izle-1/" to "Belgesel Filmleri",
        "${mainUrl}/load/page/sayfano/genres/bilim-kurgu-filmlerini-izleyin-3/" to "Bilim Kurgu Filmleri",
        "${mainUrl}/load/page/sayfano/genres/komedi-filmlerini-izleyin-1/" to "Komedi Filmleri",
        "${mainUrl}/load/page/sayfano/genres/korku-filmlerini-izle-4/" to "Korku Filmleri",
        "${mainUrl}/load/page/sayfano/genres/romantik-filmleri-izle-2/" to "Romantik Filmleri",
        "${mainUrl}/load/page/sayfano/genres/suc-filmleri-izle-3/" to "Suç Filmleri",
        "${mainUrl}/load/page/sayfano/genres/tarih-filmleri-izle-4/" to "Tarih Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val url = request.data.replace("sayfano", page.toString())
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "*/*", "X-Requested-With" to "fetch"
        )
        val doc = app.get(url, headers = headers, referer = mainUrl, interceptor = interceptor)
        val home: List<SearchResponse>?
        if (!doc.toString().contains("Sayfa Bulunamadı")) {
            val aa: HDFC = objectMapper.readValue(doc.toString())
            val document = Jsoup.parse(aa.html)

            home = document.select("a").mapNotNull { it.toSearchResult() }
            return newHomePageResponse(request.name, home)
        }
        return newHomePageResponse(request.name, emptyList())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title")
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score = this.selectFirst("span.imdb")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get(
            "${mainUrl}/search?q=${query}",
            headers = mapOf("X-Requested-With" to "fetch")
        ).parsedSafe<Results>() ?: return emptyList()
        val searchResults = mutableListOf<SearchResponse>()

        response.results.forEach { resultHtml ->
            val document = Jsoup.parse(resultHtml)

            val title = document.selectFirst("h4.title")?.text() ?: return@forEach
            val href = fixUrlNull(document.selectFirst("a")?.attr("href")) ?: return@forEach
            val posterUrl = fixUrlNull(document.selectFirst("img")?.attr("src")) ?: fixUrlNull(
                document.selectFirst("img")?.attr("data-src")
            )

            searchResults.add(
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl?.replace("/thumb/", "/list/")
                }
            )
        }

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title = document.selectFirst("h1.section-title")?.text()?.substringBefore(" izle")
            ?: return null
        val poster = fixUrlNull(
            document.select("aside.post-info-poster img.lazyload").lastOrNull()?.attr("data-src")
        )
        val tags = document.select("div.post-info-genres a").map { it.text() }
        val year =
            document.selectFirst("div.post-info-year-country a")?.text()?.trim()?.toIntOrNull()
        val tvType = if (document.select("div.seasons").isEmpty()) TvType.Movie else TvType.TvSeries
        val description = document.selectFirst("article.post-info-content > p")?.text()?.trim()
        val rating =
            document.selectFirst("div.post-info-imdb-rating span")?.text()?.substringBefore("(")
                ?.trim()
        val actors = document.select("div.post-info-cast a").map {
            Actor(it.selectFirst("strong")!!.text(), it.select("img").attr("data-src"))
        }

        val recommendations =
            document.select("div.section-slider-container div.slider-slide").mapNotNull {
                val recName = it.selectFirst("a")?.attr("title") ?: return@mapNotNull null
                val recHref =
                    fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
                val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
                    ?: fixUrlNull(it.selectFirst("img")?.attr("src"))

                newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                    this.posterUrl = recPosterUrl
                }
            }

        return if (tvType == TvType.TvSeries) {
            val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")
                ?.substringAfter("trailer/", "")
                ?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            val episodes = document.select("div.seasons-tab-content a").mapNotNull {
                val epName = it.selectFirst("h4")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
                val epEpisode =
                    Regex("""(\d+)\. ?Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                val epSeason =
                    Regex("""(\d+)\. ?Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                        ?: 1

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val trailer = document.selectFirst("div.post-info-trailer button")?.attr("data-modal")
                ?.substringAfter("trailer/", "")
                ?.let { if (it.isNotEmpty()) "https://www.youtube.com/watch?v=$it" else null }
            Log.d("HDCH", "Trailer: $trailer")
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun dcHello(base64Input: String): String {
        val decodedOnce = base64Decode(base64Input)
        val reversedString = decodedOnce.reversed()
        val decodedTwice = base64Decode(reversedString)
        val link = if (decodedTwice.contains("+")) {
            decodedTwice.substringAfterLast("+")
        } else if (decodedTwice.contains(" ")) {
            decodedTwice.substringAfterLast(" ")
        } else if (decodedTwice.contains("|")) {
            decodedTwice.substringAfterLast("|")
        } else {
            decodedTwice
        }
        return link
    }

    fun dcNew(parts: List<String>): String {
        val value = parts.joinToString("")
        val decodedBytes = base64DecodeArray(value)
        val rot13Bytes = decodedBytes.map { byte ->
            val c = byte.toInt()
            when (c) {
                in 'a'.code..'z'.code -> {
                    val base = 'a'.code
                    (((c - base + 13) % 26) + base).toByte()
                }

                in 'A'.code..'Z'.code -> {
                    val base = 'A'.code
                    (((c - base + 13) % 26) + base).toByte()
                }

                else -> {
                    byte
                }
            }
        }.toByteArray()
        val reversedBytes = rot13Bytes.reversedArray()
        val unmixedBytes = ByteArray(reversedBytes.size)
        for (i in reversedBytes.indices) {
            val charCode = reversedBytes[i].toInt() and 0xFF
            val offset = 399756995 % (i + 5)
            val newCharCode = floorMod(charCode - offset, 256)
            unmixedBytes[i] = newCharCode.toByte()
        }
        return String(unmixedBytes, Charsets.ISO_8859_1)
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("HDCH", "data » $data")
        val document = app.get(data, interceptor = interceptor).document

        document.select("div.alternative-links").map { element ->
            element to element.attr("data-lang").uppercase()
        }.forEach { (element, langCode) ->
            element.select("button.alternative-link").map { button ->
                button.text().replace("(HDrip Xbet)", "")
                    .trim() + " $langCode" to button.attr("data-video")
            }.forEach { (source, videoID) ->
                val apiGet = app.get(
                    "${mainUrl}/video/$videoID/", interceptor = interceptor,
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "X-Requested-With" to "fetch"
                    ),
                    referer = data
                ).text
                Log.d("HDCH", "Found videoID: $videoID")
                var iframe = Regex("""data-src=\\"([^"]+)""").find(apiGet)?.groupValues?.get(1)!!
                    .replace("\\", "")
                Log.d("HDCH", "iframe » $iframe")
                iframe = iframe.replace("{rapidrame_id}", "")
                Log.d("HDCH", "iframe » $iframe")
                /*if (iframe.contains("hdfilmcehennemi.mobi")){
                    loadExtractor(iframe, data, subtitleCallback, callback)
                } else {
                    invokeLocalSource(source, iframe, subtitleCallback, callback)
                }*/
                loadExtractor(iframe, data, subtitleCallback, callback)
                Log.d("HDCH", "$source » $videoID » $iframe")
            }
        }
        return true
    }

    private data class SubSource(
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
        @JsonProperty("language") val language: String? = null,
        @JsonProperty("kind") val kind: String? = null
    )

    data class Results(
        @JsonProperty("results") val results: List<String> = arrayListOf()
    )

    data class HDFC(
        @JsonProperty("html") val html: String,
        @JsonProperty("meta") val meta: Meta
    )

    data class Meta(
        @JsonProperty("title") val title: String,
        @JsonProperty("canonical") val canonical: Boolean,
        @JsonProperty("keywords") val keywords: Boolean
    )
}