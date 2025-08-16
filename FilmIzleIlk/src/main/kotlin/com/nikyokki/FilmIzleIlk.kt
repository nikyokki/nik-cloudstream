package com.nikyokki

import android.util.Base64
import android.util.Log
import com.lagradost.cloudstream3.Actor
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
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FilmIzleIlk : MainAPI() {
    override var mainUrl = "https://www.filmizleilk.net"
    override var name = "Filmİzleİlk"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/page/" to "Son Filmler",
        "${mainUrl}/film/aile-filmleri/page/" to "Aile",
        "${mainUrl}/film/aksiyon-filmleri/page/" to "Aksiyon",
        "${mainUrl}/film/amazon-prime-filmleri/page/" to "Amazon Prime",
        "${mainUrl}/film/animasyon-filmleri/page/" to "Animasyon",
        "${mainUrl}/film/ask-filmleri/page/" to "Aşk",
        "${mainUrl}/film/belgesel-filmleri/page/" to "Belgesel",
        "${mainUrl}/film/bilimkurgu-filmleri/page/" to "Bilim Kurgu",
        "${mainUrl}/film/biyografi-filmleri/page/" to "Biyografi",
        "${mainUrl}/film/cizgi-filmler/page/" to "Çizgi",
        "${mainUrl}/film/cocuk-filmleri/page/" to "Çocuk",
        "${mainUrl}/film/dram-filmleri/page/" to "Dram",
        "${mainUrl}/film/fantastik-filmler/page/" to "Fantastik",
        "${mainUrl}/film/gelecek-filmler/page/" to "Gelecek",
        "${mainUrl}/film/gerilim-filmleri/page/" to "Gerilim",
        "${mainUrl}/film/gizemli-filmler/page/" to "Gizemli",
        "${mainUrl}/film/hint-filmleri/page/" to "Hint",
        "${mainUrl}/film/komedi-filmleri/page/" to "Komedi",
        "${mainUrl}/film/kore-filmleri/page/" to "Kore",
        "${mainUrl}/film/korku-filmleri/page/" to "Korku",
        "${mainUrl}/film/macera-filmleri/page/" to "Macera",
        "${mainUrl}/film/muzikal-filmler/page/" to "Müzikal",
        "${mainUrl}/film/netflix-filmleri/page/" to "Netflix",
        "${mainUrl}/film/nette-ilk-filmler/page/" to "Nette İlk",
        "${mainUrl}/film/polisiye-filmler/page/" to "Polisiye",
        "${mainUrl}/film/romantik-filmler/page/" to "Romantik",
        "${mainUrl}/film/savas-filmleri/page/" to "Savaş",
        "${mainUrl}/film/spor-filmleri/page/" to "Spor",
        "${mainUrl}/film/suc-filmleri/page/" to "Suç",
        "${mainUrl}/film/tarihi-filmler/page/" to "Tarihi",
        "${mainUrl}/film/tavsiye-filmler/page/" to "Tavsiye",
        "${mainUrl}/film/turk-filmleri/page/" to "Türk",
        "${mainUrl}/film/western-filmler/page/" to "Western",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        var select = "div.movie-box"
        if (request.name == "Son Filmler") {
            select = "div.home-con div.movie-box"
        }
        val home = document.select(select).mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.name a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("div.name a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.img img")?.attr("src"))

        val score = this.selectFirst("div.rating span")?.text()?.trim()

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

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}").document

        return document.select("div.movie-box").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title =
            document.selectFirst("div.film h1")?.text()?.trim() ?: document.selectFirst("h1.film")
                ?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("div.description")?.text()?.trim()
        var tags = document.select("ul.post-categories a").map { it.text() }
        val rating = document.selectFirst("div.imdb-count")?.text()?.trim()?.split(" ")?.first()
        val year = Regex("""(\d+)""").find(
            document.selectFirst("li.release")?.text()?.trim() ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()
        val duration = Regex("""(\d+)""").find(
            document.selectFirst("li.time")?.text()?.trim() ?: ""
        )?.groupValues?.get(1)?.toIntOrNull()
        val recommendations = document.select("div.movie-box").mapNotNull { it.toSearchResult() }
        val actors = document.select("[href*='oyuncular']").map {
            Actor(it.text())
        }

        if (url.contains("/dizi/")) {
            tags = document.select("div.category a").map { it.text() }

            val episodes = document.select("div.episode-box").mapNotNull {
                val epHref =
                    fixUrlNull(it.selectFirst("div.name a")?.attr("href")) ?: return@mapNotNull null
                val ssnDetail =
                    it.selectFirst("span.episodetitle")?.ownText()?.trim() ?: return@mapNotNull null
                val epDetail = it.selectFirst("span.episodetitle b")?.ownText()?.trim()
                    ?: return@mapNotNull null
                val epName = "$ssnDetail - $epDetail"
                val epSeason = ssnDetail.substringBefore(". ").toIntOrNull()
                val epEpisode = epDetail.substringBefore(". ").toIntOrNull()

                newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.score = Score.from10(rating)
            this.duration = duration
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // val atobKey = Regex("""atob\("(.*)"\)""").find(sourceCode)?.groupValues?.get(1) ?: return ""

        // return Jsoup.parse(String(Base64.decode(atobKey))).selectFirst("iframe")?.attr("src") ?: ""

        val atob = Regex("""PHA\+[0-9a-zA-Z+/=]*""").find(sourceCode)?.value ?: return ""

        val padding = 4 - atob.length % 4
        val atobPadded = if (padding < 4) atob.padEnd(atob.length + padding, '=') else atob

        val iframe = Jsoup.parse(String(Base64.decode(atobPadded, Base64.DEFAULT), Charsets.UTF_8))

        return fixUrlNull(iframe.selectFirst("iframe")?.attr("src")) ?: ""
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FII", "data » $data")
        val document = app.get(data).document
        val iframes = mutableSetOf<String>()

        val mainFrame = document.selectFirst("iframe")?.attr("src")
        Log.d("FII", "mainFrame » $mainFrame")
        iframes.add(mainFrame!!)

        document.select("div.parts-middle").forEach {
            val alternatif = it.selectFirst("a")?.attr("href")
            if (alternatif != null) {
                val alternatifDocument = app.get(alternatif).document
                val alternatifFrame = getIframe(alternatifDocument.html())
                iframes.add(alternatifFrame)
            }
        }

        for (iframe in iframes) {
            Log.d("FII", "iframe » $iframe")
            if (iframe.contains("vidmoly")) {
                val headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                    "Sec-Fetch-Dest" to "iframe"
                )
                val iSource = app.get(iframe, headers = headers, referer = "${mainUrl}/").text
                val m3uLink = Regex("""file:"([^"]+)""").find(iSource)?.groupValues?.get(1)
                    ?: throw ErrorLoadingException("m3u link not found")

                Log.d("Kekik_VidMoly", "m3uLink » $m3uLink")

                callback.invoke(
                    newExtractorLink(
                        source = "VidMoly",
                        name = "VidMoly",
                        url = m3uLink,
                        type = INFER_TYPE
                    ) {
                        this.referer = "https://vidmoly.to/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }
}
