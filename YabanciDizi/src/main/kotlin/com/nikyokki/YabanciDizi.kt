package com.nikyokki

import CryptoJS
import android.util.Log
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
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class YabanciDizi : MainAPI() {
    override var mainUrl = "https://yabancidizi.so"
    override var name = "YabanciDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // ! CloudFlare bypass
    override var sequentialMainPage =
        true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay = 250L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 250L  // ? 0.05 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Güvenlik taramasından geçiriliyorsunuz. Lütfen bekleyiniz..")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi/tur/aile-izle" to "Aile",
        "${mainUrl}/dizi/tur/aksiyon-izle-1" to "Aksiyon",
        "${mainUrl}/dizi/tur/bilim-kurgu-izle-1" to "Bilim Kurgu",
        "${mainUrl}/dizi/tur/belgesel" to "Belgesel",
        "${mainUrl}/dizi/tur/bilim-kurgu-izle-1" to "Bilim Kurgu",
        "${mainUrl}/dizi/tur/dram-izle" to "Dram",
        "${mainUrl}/dizi/tur/fantastik-izle" to "Fantastik",
        "${mainUrl}/dizi/tur/gerilim-izle" to "Gerilim",
        "${mainUrl}/dizi/tur/gizem-izle" to "Gizem",
        "${mainUrl}/dizi/tur/komedi-izle" to "Komedi",
        "${mainUrl}/dizi/tur/korku-izle" to "Korku",
        "${mainUrl}/dizi/tur/macera-izle" to "Macera",
        "${mainUrl}/dizi/tur/romantik-izle-1" to "Dram",
        "${mainUrl}/dizi/tur/suc" to "Suç",
        "${mainUrl}/dizi/tur/kore-dizileri" to "Kore Dizileri",
        "${mainUrl}/dizi/tur/stand-up" to "Stand Up",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/${page}").document
        val home = document.select("div.mofy-movbox").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.mofy-movbox-text a")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score = this.selectFirst("div.mofy-movpoint span")?.text()?.trim()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/search?qr=$query",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
        )

        val parsedSafe = response.parsedSafe<JsonResponse>()
        val results = mutableListOf<SearchResponse>()

        if (parsedSafe?.success == 1) {
            parsedSafe.data.result.forEach {
                println("    s_type: ${it.s_type}")
                println("    s_link: ${it.s_link}")
                println("    s_name: ${it.s_name}")
                println("    s_image: ${it.s_image}")
                println("    s_year: ${it.s_year}")
                val title = it.s_name
                val posterUrl = fixUrlNull("$mainUrl/uploads/series/${it.s_image}") ?: ""
                if (it.s_type == "0") {
                    val href = fixUrlNull("$mainUrl/dizi/${it.s_link}") ?: ""
                    results.add(newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                        this.posterUrl = posterUrl
                    })
                } else if (it.s_type == "1") {
                    val href = fixUrlNull("$mainUrl/film/${it.s_link}") ?: ""
                    results.add(newMovieSearchResponse(title, href, TvType.Movie) {
                        this.posterUrl = posterUrl
                    })
                }
            }
        }

        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        )
        val document = app.get(url, referer = mainUrl, headers = headers).document

        val title = document.selectFirst("h1.page-title")?.text()?.trim() ?: "Title"
        val poster = fixUrlNull(document.selectFirst("div#series-profile-wrapper img")?.attr("src"))
            ?: ""
        val year =
            document.selectFirst("h1 span")?.text()?.substringAfter("(")?.substringBefore(")")
                ?.toIntOrNull()
        val description = document.selectFirst("div.series-summary-wrapper p")?.text()?.trim()
        val tags = mutableListOf<String>()
        document.selectFirst("div.ui.list")?.select("a")?.forEach {
            if (!it.attr("href").contains("/oyuncu/")) {
                tags.add(it.text().trim())
            }
        }
        val rating = document.selectFirst("div.color-imdb")?.text()?.trim()
        val duration =
            document.selectXpath("//div[text()='Süre']//following-sibling::div").text().trim()
                .split(" ").first().toIntOrNull()
        val trailer = document.selectFirst("div.media-trailer")?.attr("data-yt")
        val actors = document.selectFirst("div.global-box")?.select("div.item")?.map {
            Actor(it.selectFirst("h5")!!.text(), fixUrlNull(it.selectFirst("img")!!.attr("src")))
        }
        if (url.contains("/dizi/")) {
            val episodes = mutableListOf<Episode>()
            document.select("div.tabular-content").forEach {
                val epSeason = it.parent()?.attr("data-season")?.toIntOrNull()
                var epEpisode = 0
                it.select("div.item").forEach ep@{ episodeElement ->
                    val epHref =
                        fixUrlNull(episodeElement.selectFirst("h6 a")?.attr("href")) ?: return@ep
                    epEpisode++
                    episodes.add(
                        newEpisode(epHref){
                            this.name = "${epSeason}. Sezon ${epEpisode}. Bölüm"
                            this.season = epSeason
                            this.episode = epEpisode
                        }
                    )
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.score = Score.from10(rating)
                this.duration = duration
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("YBD", "data » ${data}")
        val document = app.get(data).document
        val timestampMillis = (System.currentTimeMillis() - 50000)
        var dilAd = ""
        document.select("div#series-tabs a").forEachIndexed { index, it ->
            val dataEid = it.attr("data-eid")
            Log.d("YBD", "dataEid -> $dataEid")
            val dataType = it.attr("data-type")
            val dilAd = if (dataType == "2") "Dublaj" else "Altyazı"
            val doc = app.post("$mainUrl/ajax/service", referer = data, headers =
            mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                "Accept" to "application/json, text/javascript, */*; q=0.01", "Cookie" to "udys=$timestampMillis",
                "X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("lang" to dataType, "episode" to dataEid, "type" to "langTab"), interceptor = interceptor).parsedSafe<Series>()
            Log.d("YBD", "dataEidDoc -> $doc")
            val doca = Jsoup.parse(doc!!.data)
            doca.select("div.item").forEach {
                val name = it.text()
                Log.d("YBD", name)
                val dataLink = it.attr("data-link")
                Log.d("YBD", dataLink)
                val dataHash = it.attr("data-hash")
                Log.d("YBD", dataHash)
                if (name.contains("Mac")) {
                    val mac = app.get(
                        "https://yabancidizi.so/api/drive/" +
                                dataLink.replace("/", "_").replace("+", "-"),
                        referer = "$mainUrl/",
                        headers =
                        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
                    ).document
                    var subFrame = mac.selectFirst("iframe")?.attr("src") ?: ""
                    if (subFrame.isEmpty()) {
                        Log.d("YBD", "subFrame boş drives denenecek")
                        val timestampInSeconds = System.currentTimeMillis() / 1000
                        Log.d("YBD", "timestampInSeconds -> $timestampInSeconds")
                        val drives = app.get(
                            "https://yabancidizi.so/api/drives/" +
                                    dataLink.replace("/", "_").replace("+", "-") + "?t=$timestampInSeconds",
                            referer = "https://yabancidizi.so/api/drives/" +
                                    dataLink.replace("/", "_").replace("+", "-"),
                            headers =
                            mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
                        ).document
                        subFrame = drives.selectFirst("iframe")?.attr("src") ?: ""
                        Log.d("YBD", "subFrame -> $subFrame")
                        if (subFrame.isEmpty()) return@forEach
                        loadMac(subFrame, callback, dilAd)
                    } else {
                        Log.d("YBD", "Else")
                        loadMac(subFrame, callback, dilAd)
                    }

                } else if (name.contains("VidMoly")) {
                    val vdm = app.get(
                        "https://yabancidizi.so/api/moly/" +
                                dataLink.replace("/", "_").replace("+", "-"), referer = "$mainUrl/",
                        headers =
                        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
                    ).document
                    val subFrame = vdm.selectFirst("iframe")?.attr("src") ?: ""
                    Log.d("YBD", "Vidmoly subFrame -> $subFrame")
                    loadExtractor(subFrame, "${mainUrl}/", subtitleCallback) { link ->
                        callback.invoke(
                            ExtractorLink(
                                source        = "$dilAd - ${link.name}",
                                name          = "$dilAd - ${link.name}",
                                url           = link.url,
                                referer       = link.referer,
                                quality       = link.quality,
                                headers       = link.headers,
                                extractorData = link.extractorData,
                                type          = link.type
                            )
                        )
                    }
                } else if (name.contains("Okru")) {
                    val okr = app.get(
                        "https://yabancidizi.so/api/ruplay/" +
                                dataLink.replace("/", "_").replace("+", "-"), referer = "$mainUrl/",
                        headers =
                        mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
                    ).document
                    val subFrame = okr.selectFirst("iframe")?.attr("src") ?: ""
                    Log.d("YBD", "Okru subFrame -> $subFrame")
                    loadExtractor(subFrame, "${mainUrl}/", subtitleCallback) { link ->
                        callback.invoke(
                            ExtractorLink(
                                source        = "$dilAd - ${link.name}",
                                name          = "$dilAd - ${link.name}",
                                url           = link.url,
                                referer       = link.referer,
                                quality       = link.quality,
                                headers       = link.headers,
                                extractorData = link.extractorData,
                                type          = link.type
                            )
                        )
                    }
                }
            }
        }


        return true
    }

    private suspend fun loadMac(subFrame: String, callback: (ExtractorLink) -> Unit, dilAd: String) {
        Log.d("YBD", "subFrame -> $subFrame")
        val iDoc = app.get(
            subFrame, referer = "${mainUrl}/",
            headers = mapOf("user-agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
        ).text
        val cryptData =
            Regex("""CryptoJS\.AES\.decrypt\("(.*)","""").find(iDoc)?.groupValues?.get(1)
                ?: ""
        val cryptPass =
            Regex("""","(.*)"\);""").find(iDoc)?.groupValues?.get(1) ?: ""
        val decryptedData = CryptoJS.decrypt(cryptPass, cryptData)
        val decryptedDoc = Jsoup.parse(decryptedData)
        val vidUrl =
            Regex("""file: '(.*)',""").find(decryptedDoc.html())?.groupValues?.get(1)
                ?: ""
        Log.d("YBD", vidUrl)
        callback.invoke(
            newExtractorLink(
                source = "$dilAd - $name",
                name = "$dilAd - $name",
                url = vidUrl,
                ExtractorLinkType.M3U8
            ) {
                this.referer = mainUrl
                this.headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                    "Referer" to mainUrl
                )
                this.quality = Qualities.Unknown.value
            }
        )
        val aa = app.get(
            vidUrl, referer = "$mainUrl/", headers =
            mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0")
        ).document.body().text()
        val urlList = extractStreamInfoWithRegex(aa)
        for (sonUrl in urlList) {
            Log.d("YBD", "sonUrl: ${sonUrl.link} -- ${sonUrl.resolution}")
            callback.invoke(
                newExtractorLink(
                    source = "$dilAd - $name -- ${sonUrl.resolution}",
                    name = "$dilAd -$name -- ${sonUrl.resolution}",
                    url = sonUrl.link,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = vidUrl
                    this.headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
                        "Referer" to vidUrl
                    )
                    this.quality = getQualityFromName(sonUrl.resolution)
                }
            )
        }

    }

    private fun extractStreamInfoWithRegex(m3uString: String): List<StreamInfo> {
        val regex =
            """#EXT-X-STREAM-INF:.*?RESOLUTION=([^\s,]+).*?(https?://[^\s]+)(?:\s|$)""".toRegex()
        val streamInfoList = regex.findAll(m3uString)
            .map { matchResult ->
                val resolution = matchResult.groupValues[1]
                val link = matchResult.groupValues[2]
                StreamInfo(resolution, link)
            }
            .toList()
        return streamInfoList
    }
}

data class StreamInfo(val resolution: String, val link: String)
