package com.keyiflerolsun

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


class Dizilla : MainAPI() {
    override var mainUrl = "https://dizilla40.com"
    override var name = "Dizilla"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true
    override var sequentialMainPageDelay = 150L
    override var sequentialMainPageScrollDelay = 150L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("verifying") || doc.html().contains("challenge-running") || doc.html().contains("cf-browser-verification")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val supportedSyncNames = setOf(
        SyncIdName.Simkl
    )

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler" to "Altyazılı Bölümler",
        "${mainUrl}/arsiv" to "Yeni Eklenen Diziler",
        "${mainUrl}/dizi-turu/aile" to "Aile",
        "${mainUrl}/dizi-turu/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/dram" to "Dram",
        "${mainUrl}/dizi-turu/fantastik" to "Fantastik",
        "${mainUrl}/dizi-turu/gerilim" to "Gerilim",
        "${mainUrl}/dizi-turu/komedi" to "Komedi",
        "${mainUrl}/dizi-turu/korku" to "Korku",
        "${mainUrl}/dizi-turu/macera" to "Macera",
        "${mainUrl}/dizi-turu/romantik" to "Romantik",
    )


    private fun extractPosterUrl(element: Element): String? {
        val img = element.selectFirst("img") ?: return null

        return fixUrlNull(img.attr("src"))
            ?: fixUrlNull(img.attr("data-src"))
            ?: fixUrlNull(img.attr("data-lazy-src"))
            ?: fixUrlNull(img.attr("data-original"))
            ?: fixUrlNull(img.attr("data-srcset")?.split(" ")?.firstOrNull())
            ?: fixUrlNull(img.attr("srcset")?.split(" ")?.firstOrNull())
            ?: fixUrlNull(img.attr("data-nimg")?.let { "https://images.macellan.online/images/tv/brand/584/386/100/$it.jpg" })
    }


    private fun extractPosterUrlFromCategory(element: Element): String? {

        val selectors = listOf(
            "img",
            "div img",
            "span img",
            "a img",
            "div.relative img",
            "div.overflow-hidden img"
        )

        for (selector in selectors) {
            val img = element.selectFirst(selector)
            if (img != null) {
                val posterUrl = extractPosterUrl(img)
                if (posterUrl != null) return posterUrl
            }
        }


        return extractPosterUrl(element)
    }


    private fun extractPosterUrlFromSonBolumler(element: Element): String? {

        val selectors = listOf(
            "img",
            "div img",
            "span img",
            "a img",
            "div.col-span-3 img",
            "div.relative img"
        )

        for (selector in selectors) {
            val img = element.selectFirst(selector)
            if (img != null) {
                val posterUrl = extractPosterUrl(img)
                if (posterUrl != null) return posterUrl
            }
        }


        return extractPosterUrl(element)
    }


    private fun extractPosterUrlFromArsiv(element: Element): String? {

        val selectors = listOf(
            "img",
            "div img",
            "span img",
            "a img",
            "div.w-full img",
            "div.relative img"
        )

        for (selector in selectors) {
            val img = element.selectFirst(selector)
            if (img != null) {
                val posterUrl = extractPosterUrl(img)
                if (posterUrl != null) return posterUrl
            }
        }


        return extractPosterUrl(element)
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var document = app.get(request.data, interceptor = interceptor).document
        val home = when {
            request.data.contains("dizi-turu") -> {

                val items = document.select("div.grid a[href*='/dizi/']").ifEmpty {
                    document.select("div.grid div.relative a[href*='/dizi/']").ifEmpty {
                        document.select("a[href*='/dizi/']").filter { it.selectFirst("img") != null }
                    }
                }

                items.mapNotNull { element ->
                    val title = element.selectFirst("h2")?.text() 
                        ?: element.attr("title")
                        ?: element.selectFirst("img")?.attr("alt")
                        ?: return@mapNotNull null

                    val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null


                    val posterUrl = extractPosterUrlFromCategory(element)

                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                }
            }
            request.data.contains("/arsiv") -> {
                val yil = Calendar.getInstance().get(Calendar.YEAR)
                val sayfa = "?page=sayi&tab=1&sort=date_desc&filterType=2&imdbMin=5&imdbMax=10&yearMin=1900&yearMax=$yil"
                val replace = sayfa.replace("sayi", page.toString())
                document = app.get("${request.data}${replace}", interceptor = interceptor).document
                document.select("a.w-full").mapNotNull { it.yeniEklenenler() }
            }
            request.data.contains("/tum-bolumler") -> {

                document.select("div.col-span-3 a").mapNotNull { element ->
                    val name = element.selectFirst("h2")?.text() ?: return@mapNotNull null
                    val epName = element.selectFirst("div.opacity-80")?.text()?.replace(". Sezon ", "x")
                        ?.replace(". Bölüm", "") ?: return@mapNotNull null

                    val title = "$name - $epName"
                    val href = fixUrlNull(element.attr("href")) ?: return@mapNotNull null


                    val posterUrl = extractPosterUrlFromSonBolumler(element)

                    newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    }
                }
            }
            else -> {

                document.select("div.col-span-3 a").mapNotNull { it.sonBolumler() }
            }
        }

        return newHomePageResponse(request.name, home)
    }



    private fun Element.yeniEklenenler(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null


        val posterUrl = extractPosterUrlFromArsiv(this)

        val score = this.selectFirst("div.absolute.bottom-0 span")?.text()?.trim()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }
    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name = this.selectFirst("h2")?.text() ?: return null
        val epName = this.selectFirst("div.opacity-80")?.text()?.replace(". Sezon ", "x")
            ?.replace(". Bölüm", "") ?: return null

        val title = "$name - $epName"

        val epDoc = fixUrlNull(this.attr("href"))?.let { app.get(it, interceptor = interceptor).document }

        val href = fixUrlNull(epDoc?.selectFirst("div.poster a")?.attr("href")) ?: return null


        val posterUrl = if (epDoc != null) {
            extractPosterUrlFromSonBolumler(epDoc.selectFirst("div.poster img") ?: epDoc.selectFirst("div.poster a") ?: epDoc)
        } else {
            extractPosterUrlFromSonBolumler(this)
        }

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun SearchItem.toSearchResponse(): SearchResponse? {
        return newTvSeriesSearchResponse(
            title ?: return null,
            "${mainUrl}/${slug}",
            TvType.TvSeries,
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
            "${mainUrl}/api/bg/searchcontent?searchterm=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
                "Accept" to "application/json, text/plain, */*",
                "Accept-Language" to "en-US,en;q=0.5",
                "X-Requested-With" to "XMLHttpRequest",
                "Sec-Fetch-Site" to "same-origin",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Dest" to "empty",
                "Referer" to "${mainUrl}/"
            ),
            referer = "${mainUrl}/",
        )
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val searchResult: SearchResult = objectMapper.readValue(searchReq.toString())
        val decodedSearch = base64Decode(searchResult.response.toString())
        val contentJson: SearchData = objectMapper.readValue(decodedSearch)
        if (contentJson.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }
        val veriler = mutableListOf<SearchResponse>()
        contentJson.result?.forEach {
            val name = it.title.toString()
            val link = fixUrl(it.slug.toString())
            val posterLink = it.poster.toString()
            val toSearchResponse = toSearchResponse(name, link, posterLink)
            veriler.add(toSearchResponse)
        }
        return veriler
    }

    private fun toSearchResponse(ad: String, link: String, posterLink: String): SearchResponse {

        val cleanPosterLink = if (posterLink.isNotEmpty() && posterLink != "null") {
            fixUrl(posterLink)
        } else {
            null
        }

        return newTvSeriesSearchResponse(
            ad,
            link,
            TvType.TvSeries,
        ) {
            this.posterUrl = cleanPosterLink
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val mainReq = app.get(url, interceptor = interceptor)
        val document = mainReq.document


        val title = document.selectFirst("div.poster h2, h1.text-2xl")?.text() ?: return null


        val posterElement = document.selectFirst("div.w-full.page-top.relative img, div.poster img") 
            ?: document.selectFirst("img[src*='images.macellan.online']") 
            ?: document.selectFirst("img")

        val poster = posterElement?.let { extractPosterUrl(it) }


        val yearElement = document.select("div.w-fit.min-w-fit, div.flex.items-center")
            .find { it.text().contains("Yapım Yılı") }
        val year = yearElement?.selectFirst("span.text-sm.opacity-60, span.opacity-60")?.text()
            ?.split(" ")?.last()?.toIntOrNull()


        val description = document.selectFirst("div.mt-2.text-sm, div.text-sm.opacity-80")?.text()?.trim()


        val tags = document.selectFirst("div.poster h3, div.flex.items-center.flex-wrap.gap-1")?.text()
            ?.split(",")?.map { it.trim() }


        val ratingString = document.selectFirst("div.flex.items-center span.text-white.text-sm, span.text-yellow-400")
            ?.text()?.trim()


        val actors = document.select("div.global-box h5, div.cast-item span").map {
            Actor(it.text())
        }

        val episodeses = mutableListOf<Episode>()


        val seasonLinks = document.select("div.flex.items-center.flex-wrap.gap-2.mb-4 a, div.seasons a")
        for (sezon in seasonLinks) {
            val sezonhref = fixUrl(sezon.attr("href"))
            val sezonReq = app.get(sezonhref, interceptor = interceptor)


            val split = sezonhref.split("-")
            val season = split.lastOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                ?: sezon.text().replace("Sezon", "").trim().toIntOrNull()

            val sezonDoc = sezonReq.document


            val episodeElements = sezonDoc.select("div.episodes div.cursor-pointer, div.episodes-box div.episode-item")
            for (bolum in episodeElements) {
                val epLink = bolum.select("a").lastOrNull() ?: continue
                val epName = epLink.text().trim()
                val epHref = fixUrlNull(epLink.attr("href")) ?: continue


                val epNumberText = bolum.selectFirst("a, span.episode-number")?.text()?.trim() ?: ""
                val epEpisode = epNumberText.replace("Bölüm", "").trim().toIntOrNull()

                val newEpisode = newEpisode(epHref) {
                    this.name = epName
                    this.season = season
                    this.episode = epEpisode
                }
                episodeses.add(newEpisode)
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.score = Score.from10(ratingString)
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data, interceptor = interceptor).document


        val script = document.selectFirst("script#__NEXT_DATA__")?.data()
        if (script != null) {
            try {
                val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
                objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                val secureData = objectMapper.readTree(script).get("props").get("pageProps").get("secureData")
                val decodedData = decryptDizillaResponse(secureData.toString().replace("\"", ""))
                val source = objectMapper.readTree(decodedData).get("RelatedResults")
                    .get("getEpisodeSources").get("result").get(0).get("source_content").toString()
                    .replace("\"", "").replace("\\", "")
                val iframe = fixUrlNull(Jsoup.parse(source).select("iframe").attr("src"))
                if (iframe != null) {
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                    return true
                }
            } catch (e: Exception) {
                Log.e("Dizilla", "Error parsing NEXT_DATA: ${e.message}")

            }
        }


        val iframes = document.select("iframe")
        if (iframes.isNotEmpty()) {
            for (iframe in iframes) {
                val src = fixUrlNull(iframe.attr("src")) ?: continue
                if (src.contains("player") || src.contains("embed") || src.contains("watch")) {
                    loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                    return true
                }
            }
        }


        val dataSrcIframes = document.select("[data-src]").filter { it.tagName() == "iframe" || it.hasAttr("data-src") }
        if (dataSrcIframes.isNotEmpty()) {
            for (iframe in dataSrcIframes) {
                val src = fixUrlNull(iframe.attr("data-src")) ?: continue
                if (src.isNotEmpty()) {
                    loadExtractor(src, "${mainUrl}/", subtitleCallback, callback)
                    return true
                }
            }
        }

        return false
    }

    private val privateAESKey = "9bYMCNQiWsXIYFWYAu7EkdsSbmGBTyUI"

    private fun decryptDizillaResponse(response: String): String? {
        try {
            val algorithm = "AES/CBC/PKCS5Padding"
            val keySpec = SecretKeySpec(privateAESKey.toByteArray(), "AES")

            val iv = ByteArray(16)
            val ivSpec = IvParameterSpec(iv)

            val cipher1 = Cipher.getInstance(algorithm)
            cipher1.init(Cipher.DECRYPT_MODE, keySpec,ivSpec)
            val firstIterationData =
                cipher1.doFinal(Base64.decode(response, Base64.DEFAULT))

            val jsonString = String(firstIterationData)

            return jsonString
        } catch (e: Exception) {
            Log.e("Dizilla", "Decryption failed: ${e.message}")
            return null
        }
    }
}
