package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.getQualityFromName
import org.jsoup.nodes.Element

class DiziGom : MainAPI() {
    override var mainUrl = "https://dizigom1.co"
    override var name = "DiziGom"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/tur/aile/" to "Aile",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/#p=$page").document
        val home = document.select("div.episode-box").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("div.serie-name a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.result-item article").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.title a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("div.title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.serieTitle h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("div.seriePoster")?.attr("style")
                ?.substringAfter("background-image:url(")?.substringBefore(")")
        )
        Log.d("DZG", "Poster: $poster")
        val description = document.selectFirst("div.serieDescription p")?.text()?.trim()
        val year = document.selectFirst("div.airDateYear a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.genreList a").map { it.text() }
        val rating = document.selectFirst("div.score")?.text()?.trim()?.toRatingInt()
        val duration = document.select("div.serieMetaInformation").select("div.totalSession")
            .last()?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val actors = document.select("div.owl-stage a")
            .map { Actor(it.text(), it.selectFirst("img")?.attr("href")) }
        //val trailer         = Regex("""embed\/(.*)\?rel""").find(document.html())?.groupValues?.get(1)?.let { "https://www.youtube.com/embed/$it" }

        val episodeses = mutableListOf<Episode>()

        document.select("div.bolumust").forEach {
            val epHref = it.selectFirst("a")?.attr("href") ?: ""
            val epName = it.selectFirst("div.bolum-ismi")?.text()
            val epSeason =
                it.selectFirst("div.baslik")?.text()?.split(" ")?.first()?.replace(".", "")
                    ?.toIntOrNull()
            val epEp = it.selectFirst("div.baslik")?.text()?.split(" ")?.get(2)?.replace(".", "")
                ?.toIntOrNull()
            episodeses.add(
                Episode(
                    data = epHref,
                    name = epName,
                    season = epSeason,
                    episode = epEp
                )
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.duration = duration
            this.rating = rating
            addActors(actors)
        }

    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title = this.selectFirst("a img")?.attr("alt") ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        Log.d("DZG", "data » ${data}")
        val document = app.get(data, referer = "$mainUrl/").document
        val embed = document.body().selectFirst("script")?.data()
        val contentJson: Gof = objectMapper.readValue(embed!!)
        Log.d("DZG","iframe » ${contentJson.contentUrl}" )
        val iframeDocument = app.get(contentJson.contentUrl, referer = "$mainUrl/").document
        val script =
            iframeDocument.select("script").find { it.data().contains("eval(function(p,a,c,k,e") }?.data()
                ?: ""
        val unpack = JsUnpacker(script).unpack()
        val sourceJ = unpack?.substringAfter("sources:[")?.substringBefore("]")?.replace("\\/", "/")
        Log.d("DZG", "sourceJ » ${sourceJ}")

        val source: Go = objectMapper.readValue(sourceJ!!)
        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = source.file,
                referer = "$mainUrl/",
                quality = getQualityFromName(source.label),
                isM3u8 = true
            )
        )

        return true
    }

    data class Go(
        @JsonProperty("file") val file: String,
        @JsonProperty("label") val label: String,
        @JsonProperty("type") val type: String
    )
    data class Gof(
        @JsonProperty("@context") val context: String,
        @JsonProperty("@type") val type: String,
        @JsonProperty("position") val position: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("thumbnailUrl") val thumbnailUrl: String,
        @JsonProperty("uploadDate") val uploadDate: String,
        @JsonProperty("duration") val duration: String,
        @JsonProperty("contentUrl") val contentUrl: String,
        @JsonProperty("timeRequired") val timeRequired: String,
        @JsonProperty("embedUrl") val embedUrl: String,
        @JsonProperty("interactionCount") val interactionCount: String,
    )
}