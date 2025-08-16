package com.nikyokki

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
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
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.HlsPlaylistParser
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class HDFilmSitesi : MainAPI() {
    override var mainUrl = "https://hdfilmsite.com"
    override var name = "HDFilmSitesi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/filmizle/aile-filmleri-izle" to "Aile",
        "${mainUrl}/filmizle/aksiyon-filmleri-izle" to "Aksiyon",
        "${mainUrl}/filmizle/animasyon-filmleri-hd-izle" to "Animasyon",
        "${mainUrl}/filmizle/bilim-kurgu-filmleri-izle" to "Bilim Kurgu",
        "${mainUrl}/filmizle/belgesel-filmleri-izle" to "Belgesel",
        "${mainUrl}/filmizle/dram-filmleri-izle" to "Dram",
        "${mainUrl}/filmizle/fantastik-filmler-izle" to "Fantastik",
        "${mainUrl}/filmizle/gerilim-filmleri-hd-izle" to "Gerilim",
        "${mainUrl}/filmizle/gizem-filmleri-izle" to "Gizem",
        "${mainUrl}/filmizle/komedi-filmleri-hd-izle" to "Komedi",
        "${mainUrl}/filmizle/korku-filmleri-izle" to "Korku",
        "${mainUrl}/filmizle/macera-filmleri-izle" to "Macera",
        "${mainUrl}/filmizle/romantik-filmler-hd-izle" to "Romantik",
        "${mainUrl}/filmizle/savas-filmleri-izle" to "Savaş",
        "${mainUrl}/filmizle/suc-filmleri-izle" to "Suç",
        "${mainUrl}/filmizle/western-filmler-hd-izle-2" to "Western",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/${page}/").document
        val home = document.select("div.movie_box").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score = this.selectFirst("span.box.imdb")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/arama/${query}").document

        return document.select("div.movie_box").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title =
            document.selectFirst("h1 span")?.text()?.substringBefore(" izle")?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description =
            document.selectFirst("div[itemprop='description']")?.text()?.substringAfter("⭐")
                ?.substringAfter("izleyin.")?.substringAfter("konusu:")?.trim()
        val year = document.selectFirst("span[itemprop='name']")?.text()?.trim()?.toIntOrNull()
        val tags =
            document.select("a[rel='category']").map { it.text().substringBefore(" Filmleri") }
        val rating =
            document.selectFirst("div.puanlar span")?.text()?.trim()?.substringAfter("IMDb")
        val duration =
            document.selectFirst("span[itemprop='duration']")?.text()?.split(" ")?.first()?.trim()
                ?.toIntOrNull()
        val actors = document.select("a.cast").map { Actor(it.text(), it.attr("href")) }
        val trailer = fixUrlNull(document.selectFirst("[property='og:video']")?.attr("content"))

        if (document.selectFirst("div.part_buton_sec")?.text()?.contains("Sezon") == true) {
            val episodes = mutableListOf<Episode>()

            val iframeSkici = IframeKodlayici()

            val pdataMatches = Regex("""pdata\[\'(.*?)'\] = \'(.*?)\';""").findAll(document.html())
            val pdataList = pdataMatches.map { it.destructured }.toList()

            for (pdata in pdataList) {
                val key = pdata.component1();
                val value = pdata.component2();

                val iframeData = iframeSkici.iframeCoz(value!!)
                val iframeLink = app.get(iframeData, referer = "${mainUrl}/").url.toString()

                //val sz_num = partNumber.takeIf { it.contains("sezon") }?.substringBefore("sezon")?.toIntOrNull() ?: 1
                //val ep_num = partName.substringBefore(".")?.trim()?.toIntOrNull() ?: 1

                val sz_num = key.substringAfter("prt_").substringBefore("sezon").toIntOrNull() ?: 1
                var ep_num = key.substringAfter("sezon").toIntOrNull()
                if (ep_num != null) {
                    ep_num += 1
                } else {
                    ep_num = 1
                }

                episodes.add(
                    newEpisode(iframeLink) {
                        this.name = "${sz_num}. Sezon ${ep_num}. Bölüm"
                        this.season = sz_num
                        this.episode = ep_num
                    }
                )
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
        } else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("HDS", "data -> $data")
        if (data.contains("vidmody")) {
            val aa = app.get(data, referer = "${mainUrl}/").document
            val bb = aa.body().selectFirst("script").toString()
                .substringAfter("var id =").substringBefore(";")
                .replace("'", "").trim()
            val m3uLink = "https://vidmody.com/vs/$bb"
            Log.d("HDS", "m3uLink -> $m3uLink")
            M3u8Helper.generateM3u8(
                name,
                m3uLink,
                "$mainUrl/"
            ).forEach(callback)
        } else if (data.contains("vidlop")) {
            val vidUrl = app.post(
                "https://vidlop.com/player/index.php?data=" + data.split("/")
                    .last() + "&do=getVideo",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                referer = "${mainUrl}/"
            ).parsedSafe<VidLop>()?.securedLink ?: return false
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = vidUrl,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = data
                    this.quality = Qualities.Unknown.value
                }
            )
            loadExtractor(data, subtitleCallback, callback)
        }
        val document = app.get(data).document
        val iframeSkici = IframeKodlayici()
        val pdataMatches = Regex("""pdata\[\'(.*?)'\] = \'(.*?)\';""").findAll(document.html())
        val pdataList = pdataMatches.map { it.destructured }.toList()

        for (pdata in pdataList) {
            val key = pdata.component1()
            val value = pdata.component2()
            val iframeData = iframeSkici.iframeCoz(value!!)
            val iframeLink = app.get(iframeData, referer = "${mainUrl}/").url.toString()
            if (iframeLink.contains("vidmody")) {
                Log.d("HDS", "iframeLink -> $iframeLink")
                val aa = app.get(iframeLink, referer = "${mainUrl}/").document
                val bb = aa.body().selectFirst("script").toString()
                    .substringAfter("var id =").substringBefore(";")
                    .replace("'", "").trim()
                val m3uLink = "https://vidmody.com/vs/$bb"
                Log.d("HDS", "m3uLink -> $m3uLink")
                M3u8Helper.generateM3u8(
                    "VidMody",
                    m3uLink,
                    "$mainUrl/"
                ).forEach(callback)

            } else if (iframeLink.contains("vidlop")) {
                val vidUrl = app.post(
                    "https://vidlop.com/player/index.php?data=" + data.split("/")
                        .last() + "&do=getVideo",
                    headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                    referer = "${mainUrl}/"
                ).parsedSafe<VidLop>()?.securedLink ?: return false
                callback.invoke(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = vidUrl,
                        ExtractorLinkType.M3U8
                    ) {
                        this.referer = data
                        this.quality = Qualities.Unknown.value
                    }
                )
                loadExtractor(data, subtitleCallback, callback)
            }
        }
        return true
    }

    data class VidLop(
        @JsonProperty("hls") val hls: Boolean? = null,
        @JsonProperty("securedLink") val securedLink: String? = null
    )
}