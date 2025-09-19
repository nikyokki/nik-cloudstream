// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
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
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class SezonlukDizi : MainAPI() {
    override var mainUrl = "https://sezonlukdizi7.com"
    override var name = "SezonlukDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override val mainPage = mainPageOf(
        "${mainUrl}/diziler.asp?siralama_tipi=id&s=" to "Son Eklenenler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&tur=mini&s=" to "Mini Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=2&s=" to "Yerli Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=1&s=" to "Yabancı Diziler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=3&s=" to "Asya Dizileri",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=4&s=" to "Animasyonlar",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=5&s=" to "Animeler",
        "${mainUrl}/diziler.asp?siralama_tipi=id&kat=6&s=" to "Belgeseller",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home = document.select("div.afis a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.description")?.text()?.trim() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        val score = this.selectFirst("span.imdbp")?.text()?.replace("IMDb", "")?.trim()

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/diziler.asp?adi=${query}").document

        return document.select("div.afis a").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("div.header")?.text()?.trim() ?: return null
        val poster =
            fixUrlNull(document.selectFirst("div.image img")?.attr("data-src")) ?: return null
        val year = document.selectFirst("div.extra span")?.text()?.trim()?.split("-")?.first()
            ?.toIntOrNull()
        val description = document.selectFirst("span#tartismayorum-konu")?.text()?.trim()
        val tags = document.select("div.labels a[href*='tur']").mapNotNull { it.text().trim() }
        val rating = document.selectFirst("div.dizipuani a div")?.text()?.trim()?.replace(",", ".")
        val duration = document.selectXpath("//span[contains(text(), 'Dk.')]").text().trim()
            .substringBefore(" Dk.").toIntOrNull()

        val endpoint = url.split("/").last()

        val actorsReq = app.get("${mainUrl}/oyuncular/${endpoint}").document
        val actors = actorsReq.select("div.doubling div.ui").map {
            Actor(
                it.selectFirst("div.header")!!.text().trim(),
                fixUrlNull(it.selectFirst("img")?.attr("src"))
            )
        }


        val episodesReq = app.get("${mainUrl}/bolumler/${endpoint}").document
        val episodes = mutableListOf<Episode>()
        for (sezon in episodesReq.select("table.unstackable")) {
            for (bolum in sezon.select("tbody tr")) {
                val epName = bolum.selectFirst("td:nth-of-type(4) a")?.text()?.trim() ?: continue
                val epHref =
                    fixUrlNull(bolum.selectFirst("td:nth-of-type(4) a")?.attr("href")) ?: continue
                val epEpisode =
                    bolum.selectFirst("td:nth-of-type(3)")?.text()?.substringBefore(".Bölüm")
                        ?.trim()?.toIntOrNull()
                val epSeason =
                    bolum.selectFirst("td:nth-of-type(2)")?.text()?.substringBefore(".Sezon")
                        ?.trim()?.toIntOrNull()

                episodes.add(newEpisode(epHref) {
                    this.name = epName
                    this.season = epSeason
                    this.episode = epEpisode
                })
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
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("SZD", "data » $data")
        val document = app.get(data).document
        val aspData = getAspData()
        val bid = document.selectFirst("div#dilsec")?.attr("data-id") ?: return false
        Log.d("SZD", "bid » $bid")

        val altyaziResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf(
                "bid" to bid,
                "dil" to "1"
            )
        ).parsedSafe<Kaynak>()
        altyaziResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»1 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("id" to "${veri.id}")
            ).document

            val iframe =
                fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»1 | iframe » $iframe")
            if (iframe.contains("ruby")) {
                extractRuby(iframe, callback, veri, "Altyazı")
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                    callback.invoke(
                        ExtractorLink(
                            source = "AltYazı - ${veri.baslik}",
                            name = "AltYazı - ${veri.baslik}",
                            url = link.url,
                            referer = link.referer,
                            quality = link.quality,
                            headers = link.headers,
                            extractorData = link.extractorData,
                            type = link.type
                        )
                    )
                }
            }
        }

        val dublajResponse = app.post(
            "${mainUrl}/ajax/dataAlternatif${aspData.alternatif}.asp",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            data = mapOf(
                "bid" to bid,
                "dil" to "0"
            )
        ).parsedSafe<Kaynak>()
        dublajResponse?.takeIf { it.status == "success" }?.data?.forEach { veri ->
            Log.d("SZD", "dil»0 | veri.baslik » ${veri.baslik}")

            val veriResponse = app.post(
                "${mainUrl}/ajax/dataEmbed${aspData.embed}.asp",
                headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
                data = mapOf("id" to "${veri.id}")
            ).document

            val iframe =
                fixUrlNull(veriResponse.selectFirst("iframe")?.attr("src")) ?: return@forEach
            Log.d("SZD", "dil»0 | iframe » $iframe")

            if (iframe.contains("ruby")) {
                extractRuby(iframe, callback, veri, "Dublaj")
            } else {
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback) { link ->
                    callback.invoke(
                        ExtractorLink(
                            source = "Dublaj - ${veri.baslik}",
                            name = "Dublaj - ${veri.baslik}",
                            url = link.url,
                            referer = link.referer,
                            quality = link.quality,
                            headers = link.headers,
                            extractorData = link.extractorData,
                            type = link.type
                        )
                    )
                }
            }
        }

        return true
    }

    private suspend fun extractRuby(
        iframe: String,
        callback: (ExtractorLink) -> Unit,
        veri: Veri,
        dil: String
    ) {
        val header = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:137.0) Gecko/20100101 Firefox/137.0",
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Referer" to "https://sezonlukdizi7.com/"
        )
        val son = app.get(
            iframe,
            referer = "https://sezonlukdizi7.com",
            headers = header
        ).document.select("script").find { it.data().contains("function(p,a,c,k,e") }?.data() ?: ""
        val unPacked = JsUnpacker(son).unpack()
        val file =
            unPacked?.substringAfter("sources:[")?.substringBefore("],")?.addMarks("file") ?: ""
        val subtitle = unPacked?.substringAfter("tracks:[")?.substringBefore(",{")?.addMarks("file")
            ?.addMarks("kind")
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        val sonFile = objectMapper.readValue<Ruby>(file)
        /*val generateM3u8 = M3u8Helper.generateM3u8(
            "StreamRuby",
            sonFile.file,
            "https://sezonlukdizi7.com/"
        )*/
        callback.invoke(
            newExtractorLink(
                source = "$dil - ${veri.baslik}",
                name = "$dil - ${veri.baslik}",
                url = sonFile.file,
                ExtractorLinkType.M3U8
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.Unknown.value
            }
        )
    }

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }

    //Helper function for getting the number (probably some kind of version?) after the dataAlternatif and dataEmbed
    private suspend fun getAspData(): AspData {
        val websiteCustomJavascript = app.get("${this.mainUrl}/js/site.min.js")
        val dataAlternatifAsp =
            Regex("""dataAlternatif(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(
                1
            )
                .toString()
        val dataEmbedAsp =
            Regex("""dataEmbed(.*?).asp""").find(websiteCustomJavascript.text)?.groupValues?.get(1)
                .toString()
        return AspData(dataAlternatifAsp, dataEmbedAsp)
    }
}
