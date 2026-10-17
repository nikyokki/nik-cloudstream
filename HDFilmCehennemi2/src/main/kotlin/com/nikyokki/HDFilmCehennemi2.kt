package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
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
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class HDFilmCehennemi2 : MainAPI() {
    override var mainUrl = "https://www.hdfilmcehennemi2.biz"
    override var name = "HDFilmCehennemi2"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/" to "Yeni Eklenenler",
        "${mainUrl}/en-cok-izlenen-filmler/" to "En Çok İzlenenler",
        "${mainUrl}/imdb-7-puan-uzeri-filmler/" to "Imdb 7+ Filmler",
        "${mainUrl}/tur/aile-filmleri/" to "Aile",
        "${mainUrl}/tur/aksiyon-filmleri-izle/" to "Aksiyon",
        "${mainUrl}/tur/animasyon-film-izle/" to "Animasyon",
        "${mainUrl}/tur/bilim-kurgu-filmleri-izle/" to "Bilim Kurgu",
        "${mainUrl}/tur/biyografi-filmleri/" to "Biyografi",
        "${mainUrl}/tur/dram-filmleri/" to "Dram",
        "${mainUrl}/tur/fantastik-filmleri/" to "Fantastik",
        "${mainUrl}/tur/gerilim-filmleri/" to "Gerilim",
        "${mainUrl}/tur/gizem-filmleri/" to "Gizem",
        "${mainUrl}/tur/komedi-filmleri/" to "Komedi",
        "${mainUrl}/tur/korku-filmleri/" to "Korku",
        "${mainUrl}/tur/macera-filmleri/" to "Macera",
        "${mainUrl}/tur/romantik-filmler/" to "Romantik",
        "${mainUrl}/tur/savas-filmleri/" to "Savaş",
        "${mainUrl}/tur/suc-filmleri/" to "Suç",
        "${mainUrl}/tur/tarih-filmleri/" to "Tarih",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}page/${page}").document
        if (request.name == "Yeni Eklenenler") {
            val home =
                document.select("div.tab-content div.poster").mapNotNull { it.toMainPageResult() }
            return newHomePageResponse(request.name, home)
        }
        val home = document.select("div.poster").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse {
        val title = this.selectFirst("div.poster-title h2")?.text()?.replace(" izle", "") ?: ""
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: ""
        val posterUrl = fixUrlNull(this.selectFirst("div.poster-image img")?.attr("data-src"))
        val score = this.selectFirst("span.bg-warning")?.text()?.trim()

        if (href.contains("/dizi/")) {
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        } else {
            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.score = Score.from10(score)
            }
        }
    }

    private fun toSearchRes(movie: Movie): SearchResponse {
        val title = movie.title ?: ""
        val posterUrl = fixUrlNull("$mainUrl/uploads/poster/${movie.poster}")
        if (movie.type == "1") {
            val href = fixUrlNull("$mainUrl/${movie.slug}") ?: ""
            return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        } else {
            val href = fixUrlNull("$mainUrl/${movie.slugPrefix}/${movie.slug}") ?: ""
            return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/search",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
            data = mapOf("query" to query)
        ).document.body().text()

        val result = mutableListOf<SearchResponse>()

        val json = ObjectMapper().readValue(response, HDSearchResponse::class.java)
        json.result.forEach {
            result.add(toSearchRes(it))
        }
        return result
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.title a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("div.title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val orgTitle =
            document.selectFirst("div.card-header h1")?.text()?.substringBefore(" izle", "")?.trim()
                ?: ""
        val altTitle = document.selectFirst("div.card-header small")?.text()?.trim() ?: ""
        val title =
            if (altTitle.isNotEmpty() && orgTitle != altTitle) "$orgTitle - $altTitle" else orgTitle
        val poster = fixUrlNull(document.selectFirst("picture.poster-auto img")?.attr("data-src"))
        val description = document.selectFirst("article.text-white p")?.text()?.trim()
        var year = document.selectFirst("div.release a")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div#listelements a").map { it.text() }
        val rating = document.selectFirst("div.rate")?.text()
        val actors = mutableListOf<Actor>()
        val trailer = document.selectFirst("div.nav-link")?.attr("data-trailer")
        val listItems = document.select("tbody tr").select("div")
        var duration = 0
        for (item in listItems) {
            if (item.selectFirst("small")?.text()?.contains("Yıl") == true) {
                year = item.selectFirst("a")?.text()?.toIntOrNull()
            }
            if (item.selectFirst("small")?.text()?.contains("Süre") == true) {
                duration =
                    item.selectFirst("strong")?.text()?.replace(" dakika", "")?.toIntOrNull()!!
            }
        }
        document.select(".story-item").forEach {
            val img = fixUrlNull(it.selectFirst("img")?.attr("data-src"))
            val name = it.selectFirst("div.story-item-title")?.text() ?: ""
            actors.add(Actor(name = name, image = img))
        }
        val recommendations =
            document.select("div.glide__slide.poster-container")
                .mapNotNull { it.toRecommendationResult() }

        if (!url.contains("/dizi/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer("https://www.youtube.com/embed/$trailer")
            }
        } else {
            val episodes = mutableListOf<Episode>()
            document.select("div.seasonsTabs-tabContent div").forEach { szn ->
                val epSzn = szn.attr("id").substringAfter("seasons-").toIntOrNull()
                szn.select("div.card-list-item").forEach {
                    val epHref = it.selectFirst("a")?.attr("href") ?: ""
                    val epName = it.selectFirst("h3")?.text()
                    val epnum =
                        epName?.substringAfter("Sezon ")?.substringBefore(". Bölüm")?.toIntOrNull()
                    episodes.add(
                        newEpisode(epHref) {
                            this.name = epName
                            this.season = epSzn
                            this.episode = epnum
                        }
                    )
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                addActors(actors)
                addTrailer(trailer)
            }
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse {
        val title = this.selectFirst("h2")?.text() ?: ""
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: ""
        val posterUrl = fixUrlNull(this.selectFirst("picture img")?.attr("data-src"))
        if (href.contains("/dizi/")) {
            return newTvSeriesSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("HDC", "data » $data")
        val document = app.get(data).document
        if (document.select("div.tab-content div").size > 1) {
            Log.d("HDC", "Alternatif 1den fazla")
            document.select("div.tab-content div").forEach {
                var name = this.name
                if (it.attr("id") == "videostr") {
                    name = "Türkçe Dublaj"
                } else if (it.attr("id") == "videosen") {
                    name = "Türkçe Altyazılı"
                }
                it.select("a").forEach { el ->
                    val url = el.attr("href")
                    if (url == data) {
                        val iframe =
                            fixUrlNull(document.selectFirst("iframe")?.attr("data-src")) ?: ""
                        Log.d("HDC", "iframe » $iframe")
                        if (iframe.contains("vidload")) {
                            vidloadExtract(iframe, name, callback, subtitleCallback)
                        } else {
                            loadExtractor(iframe, url, subtitleCallback, callback)
                        }
                    } else {
                        val doc = app.get(url).document
                        val iframe = fixUrlNull(doc.selectFirst("iframe")?.attr("data-src")) ?: ""
                        Log.d("HDC", "iframe » $iframe")
                        if (iframe.contains("vidload")) {
                            vidloadExtract(iframe, name, callback, subtitleCallback)
                        } else {
                            loadExtractor(iframe, url, subtitleCallback, callback)
                        }
                    }
                }
            }
        } else {
            val iframe = fixUrlNull(document.selectFirst("iframe")?.attr("data-src")) ?: ""
            val name =
                document.selectFirst("div.card-body")?.selectFirst("li.nav-item a")?.text() ?: ""
            if (iframe.contains("vidload")) {
                vidloadExtract(iframe, name, callback, subtitleCallback)
            } else {
                loadExtractor(iframe, data, subtitleCallback, callback)
            }
            document.select("div#videosdual a").forEachIndexed { index, element ->
                if (index == 0) return@forEachIndexed
                val url = element.attr("href")
                val doc = app.get(url).document
                val ifAlt = fixUrlNull(doc.selectFirst("iframe")?.attr("data-src")) ?: ""
                if (ifAlt.contains("vidload")) {
                    vidloadExtract(ifAlt, name, callback, subtitleCallback)
                } else {
                    loadExtractor(ifAlt, url, subtitleCallback, callback)
                }
            }

        }
        return true
    }

    suspend fun vidloadExtract(
        iframe: String,
        name: String,
        callback: (ExtractorLink) -> Unit,
        subtitleCallback: (SubtitleFile) -> Unit
    ) {
        Log.d("HDC", "vidloadExtract » $iframe")
        if (iframe.contains("vidload")) {
            //val url = iframe.replace("/iframe/", "/ajax/")
            Log.d("HDC", "vidloadExtract » $iframe")
            val doc = app.get(
                iframe,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36",
                ), referer = mainUrl
            ).document
            val source = "https://vidload.site" + doc.selectFirst("source")?.attr("src")
            callback.invoke(
                newExtractorLink(
                    source = "Vidload",
                    name = "Vidload $name",
                    url = source,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = "https://vidload.site/"
                    this.quality = Qualities.Unknown.value
                }
            )
            val script =
                doc.select("script").find { it.data().contains("player.addRemoteTextTrack") }
                    ?.data() ?: ""
            val regex =
                Regex("""src:\s*'([^']*)'.*?label:\s*'([^']*)'""", RegexOption.DOT_MATCHES_ALL)
            val matches = regex.findAll(script)
            for (match in matches) {
                val src = match.groupValues[1]
                val label = match.groupValues[2]
                subtitleCallback.invoke(
                    SubtitleFile(
                        label,
                        "https://vidload.site$src"
                    )
                )
            }
        }
    }
}

