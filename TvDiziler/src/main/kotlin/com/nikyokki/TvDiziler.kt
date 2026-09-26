package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.extractors.JWPlayer
import org.jsoup.Jsoup

class TvDiziler : MainAPI() {
    override var mainUrl              = "https://tvdiziler.tv"
    override var name                 = "TvDiziler"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        mainUrl                         to "Son Bölümler",
        "${mainUrl}/dizi/tur/aile"      to "Aile",
        "${mainUrl}/dizi/tur/aksiyon"      to "Aksiyon",
        "${mainUrl}/dizi/tur/aksiyon-macera"      to "Aksiyon-Macera",
        "${mainUrl}/dizi/tur/bilim-kurgu-fantazi"      to "Bilim Kurgu & Fantazi",
        "${mainUrl}/dizi/tur/fantastik"      to "Fantastik",
        "${mainUrl}/dizi/tur/gerilim"      to "Gerilim",
        "${mainUrl}/dizi/tur/gizem"      to "Gizem",
        "${mainUrl}/dizi/tur/komedi"      to "Komedi",
        "${mainUrl}/dizi/tur/korku"      to "Korku",
        "${mainUrl}/dizi/tur/macera"      to "Macera",
        "${mainUrl}/dizi/tur/pembe-dizi"      to "Pembe Dizi",
        "${mainUrl}/dizi/tur/romantik"      to "Romantik",
        "${mainUrl}/dizi/tur/savas"      to "Savaş",
        "${mainUrl}/dizi/tur/savas-politik"      to "Savaş & Politik",
        "${mainUrl}/dizi/tur/suc"      to "Suç",
        "${mainUrl}/dizi/tur/talk"      to "Talk",
        "${mainUrl}/dizi/tur/tarih"      to "Tarih",
        "${mainUrl}/dizi/tur/yarisma"      to "Yarışma",

    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.name == "Son Bölümler") {
            val mainReq = app.get(request.data)
            val document = mainReq.document.body()
            val home = document.select("div.poster-xs").mapNotNull { it.sonBolumler() }
            return newHomePageResponse(request.name, home)
        }
        val mainReq = app.get("${request.data}/${page}")
        val document = mainReq.document.body()
        val home = document.select("div.poster-long").mapNotNull { it.diziler() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title =
            this.selectFirst("div.poster-long-subject h2")?.text()?.replace(" izle", "") ?: return null
        val href =
            fixUrlNull(this.selectFirst("div.poster-long-subject a")?.attr("href"))
                ?: return null
        val posterUrl =
            fixUrlNull(this.selectFirst("div.poster-long-image img")?.attr("data-src"))
        val score = this.selectFirst("span.rating")?.text()?.trim()

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }
    }

    private fun Element.sonBolumler(): SearchResponse? {
        val title =
            this.selectFirst("div.poster-xs-subject p")?.text()?.replace(" izle", "") ?: return null
        val href =
            fixUrlNull(this.selectFirst("a")?.attr("href"))
                ?: return null
        val posterUrl =
            fixUrlNull(this.selectFirst("div.poster-xs-image img")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toPostSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3.truncate")?.text()?.trim()?.replace(" izle", "") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        if (href.contains("dizi/")) {
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchReq = app.post(
            "${mainUrl}/search?qr=$query",
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
            ),
            referer = "${mainUrl}/"
        )

        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        val searchResult: SearchResult = objectMapper.readValue(searchReq.toString())

        if (searchResult.success != 1) {
            throw ErrorLoadingException("Invalid Json response")
        }

        val searchDoc = searchResult.data

        val document = Jsoup.parse(searchDoc.toString())
        val results = mutableListOf<SearchResponse>()

        document.select("ul li").forEach { listItem ->
            val href = listItem.selectFirst("a")?.attr("href")
            if (href != null && (href.contains("dizi/") || href.contains("film/"))) {
                val result = listItem.toPostSearchResult()
                result?.let { results.add(it) }
            }
        }
        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        Log.d("TVD", "Load -> url")
        println("Load -> url")
        if (!url.contains("/dizi/")) {
            val mainReq = app.get(url, referer = mainUrl)
            val document = mainReq.document
            val title = document.selectFirst("div.page-title h1")?.text()?.replace(" izle", "") ?: "return null"
            val epEpisode = document.selectFirst("div.page-title h1 span")?.text()?.replace(" izle", "")?.toIntOrNull()
            val poster = document.selectFirst("meta[property=og:image]")?.attr("content")
            val description = document.selectFirst("meta[name=og:description]")?.attr("content")
            val episodeses = mutableListOf<Episode>()
            episodeses.add(
                newEpisode(url) {
                    this.name = title
                    this.season = 1
                    this.episode = epEpisode
                })

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
                this.posterUrl = poster
                this.plot = description
            }

        }
        val mainReq = app.get(url, referer = mainUrl)
        val document = mainReq.document
        val title = document.selectFirst("div.page-title p")?.text()?.replace(" izle", "") ?: "return null"
        val poster =
            fixUrlNull(document.selectFirst("div.series-profile-image img")?.attr("data-src"))
        val year =
            document.selectFirst("h1 span")?.text()?.substringAfter("(")?.substringBefore(")")
                ?.toIntOrNull()

        val rating = document.selectXpath("//span[text()='IMDb Puanı']//following-sibling::p").text().trim()

        val duration =
            document.selectXpath("//span[text()='Süre']//following-sibling::p").text().trim()
                .split(" ").first().toIntOrNull()

        val description = document.selectFirst("div.series-profile-summary p")?.text()?.trim()

        val tags = document.selectFirst("div.series-profile-type")?.select("a")
            ?.mapNotNull { it.text().trim() }
        val trailer = document.selectFirst("div.series-profile-trailer")?.attr("data-yt")
        val actors = mutableListOf<Actor>()
        document.select("div.series-profile-cast li").forEach {
            val img = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            val name = it.selectFirst("h5.truncate")?.text()?.trim() ?: return null
            actors.add(Actor(name, img))
        }
        if (url.contains("/dizi/")) {
            val episodeses = mutableListOf<Episode>()
            var szn = 1
            for (sezon in document.select("div.series-profile-episode-list")) {
                var blm = 1
                for (bolum in sezon.select("li")) {
                    val epName = bolum.selectFirst("h6.truncate a")?.text() ?: continue
                    val epHref = fixUrlNull(bolum.select("h6.truncate a").attr("href")) ?: continue
                    val epEpisode = blm++
                    val epSeason = szn
                    episodeses.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = epSeason
                            this.episode = epEpisode
                        })
                }
                szn++
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/${trailer}")
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val headers = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "$mainUrl/"
        )

        val document = app.get(
            data, headers = headers
        ).document
        val iframe =
            fixUrlNull(document.selectFirst("li.series-alter-active button")?.attr("data-hhs")) ?: return false
        Log.d("TVD", "Iframe -> $iframe")
        if (iframe.contains("youtube.com")) {
            val id = iframe.substringAfter("/embed/").substringBefore("?")
            loadExtractor(
                "https://youtube.com/watch?v=$id",
                subtitleCallback,
                callback
            )
            callback(
                newExtractorLink(
                    "Youtube",
                    "Youtube",
                    "https://nyc1.ivc.ggtyler.dev/api/manifest/dash/id/$id",
                    ExtractorLinkType.DASH
                ) {
                    this.referer = ""
                    this.headers = mapOf()
                    this.quality = Qualities.Unknown.value
                    this.extractorData = null
                }
            )
        } else {
            val ifDoc = app.get(
                iframe,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                    "Accept-Language" to "en-US,en;q=0.5"),
                referer = mainUrl
            ).document
            val script = ifDoc.select("script").find { it.data().contains("sources:") }?.data() ?: ""
            val videoData = script.substringAfter("sources: [")
                .substringBefore("],").addMarks("file").addMarks("type").addMarks("label")
            val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            val source: TvDiziFile = objectMapper.readValue(videoData)
            if (source.type == "hls") {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = source.file,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(source.label)
                    }
                )
            } else if (source.type == "mp4") {
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = source.file,
                        ExtractorLinkType.VIDEO
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = getQualityFromName(source.label)
                    }
                )
            }
        }
        return true
    }
    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }
}

data class TvDiziFile(
    @JsonProperty("file") val file: String,
    @JsonProperty("label") val label: String,
    @JsonProperty("type") val type: String
)


class TvDizilerOynat : JWPlayer() {
    override val name = "TvDizilerOynat"
    override val mainUrl = "https://tvdiziler.tv/player/oynat/"
}