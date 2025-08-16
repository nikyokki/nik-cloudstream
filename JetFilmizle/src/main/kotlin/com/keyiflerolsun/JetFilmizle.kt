// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.Actor
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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Element

class JetFilmizle : MainAPI() {
    override var mainUrl = "https://jetfilmizle.ltd"
    override var name = "JetFilmizle"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        mainUrl to "Son Filmler",
        "${mainUrl}/netflix" to "Netflix",
        "${mainUrl}/editorun-secimi" to "Editörün Seçimi",
        "${mainUrl}/turk-film-full-hd-izle" to "Türk Filmleri",
        "${mainUrl}/cizgi-filmler-full-izle" to "Çizgi Filmler",
        "${mainUrl}/kategoriler/yesilcam-filmleri-full-izle" to "Yeşilçam Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var url = request.data
        if (page > 1) {
            url = "$url/page/$page"
        }
        val document = app.get(url).document
        val home = document.select("article.movie").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        var title = this.selectFirst("h2 a")?.text() ?: this.selectFirst("h3 a")?.text()
        ?: this.selectFirst("h4 a")?.text() ?: this.selectFirst("h5 a")?.text() ?: this.selectFirst(
            "h6 a"
        )?.text() ?: return null
        title = title.substringBefore(" izle")

        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        var posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        if (posterUrl == null) {
            posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        }
        val score = this.selectFirst("span.puan_1")?.text()?.trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post(
            "${mainUrl}/filmara.php",
            referer = "${mainUrl}/",
            data = mapOf("s" to query)
        ).document

        return document.select("article.movie").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("section.movie-exp div.movie-exp-title")?.text()
            ?.substringBefore(" izle")?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("data-src"))
            ?: fixUrlNull(document.selectFirst("section.movie-exp img")?.attr("src"))
        val yearDiv =
            document.selectXpath("//div[@class='yap' and contains(strong, 'Vizyon') or contains(strong, 'Yapım')]")
                .text().trim()
        val year = Regex("""(\d{4})""").find(yearDiv)?.groupValues?.get(1)?.toIntOrNull()
        val description = document.selectFirst("section.movie-exp p.aciklama")?.text()?.trim()
        val tags = document.select("section.movie-exp div.catss a").map { it.text() }
        val rating =
            document.selectFirst("section.movie-exp div.imdb_puan span")?.text()?.split(" ")?.last()
        val actors = document.select("section.movie-exp div.oyuncu").map {
            Actor(
                it.selectFirst("div.name")!!.text(),
                fixUrlNull(it.selectFirst("img")!!.attr("data-src"))
            )
        }

        val recommendations = document.select("div#benzers article").mapNotNull {
            var recName = it.selectFirst("h2 a")?.text() ?: it.selectFirst("h3 a")?.text()
            ?: it.selectFirst("h4 a")?.text() ?: it.selectFirst("h5 a")?.text()
            ?: it.selectFirst("h6 a")?.text() ?: return@mapNotNull null
            recName = recName.substringBefore(" izle")

            val recHref = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val recPosterUrl = fixUrlNull(it.selectFirst("img")?.attr("data-src"))

            newMovieSearchResponse(recName, recHref, TvType.Movie) {
                this.posterUrl = recPosterUrl
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.score = Score.from10(rating)
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("JTF", "data » $data")
        val document = app.get(data).document

        val iframes = mutableListOf<String>()
        val mainIframe =
            fixUrlNull(document.selectFirst("div#movie iframe")?.attr("data-src")) ?: fixUrlNull(
                document.selectFirst("div#movie iframe")?.attr("data-litespeed-src")
            ) ?: fixUrlNull(document.selectFirst("div#movie iframe")?.attr("data")) ?: fixUrlNull(
                document.selectFirst("div#movie iframe")?.attr("src")
            )
        Log.d("JTF", "mainIframe » $mainIframe")
        if (mainIframe != null) {
            iframes.add(mainIframe)
        }

        document.select("div.film_part a").forEach {
            val source = it.selectFirst("span")?.text()?.trim() ?: return@forEach
            if (source.lowercase().contains("fragman")) return@forEach

            val movDoc = app.get(it.attr("href")).document
            val iframe =
                fixUrlNull(movDoc.selectFirst("div#movie iframe")?.attr("data-src")) ?: fixUrlNull(
                    movDoc.selectFirst("div#movie iframe")?.attr("data")
                ) ?: fixUrlNull(movDoc.selectFirst("div#movie iframe")?.attr("src"))
            Log.d("JTF", "iframe » $iframe")

            if (iframe != null) {
                iframes.add(iframe)
            } else {
                movDoc.select("div#movie p a").forEach downloadLinkForEach@{ link ->
                    val downloadLink = fixUrlNull(link.attr("href")) ?: return@downloadLinkForEach
                    iframes.add(downloadLink)
                }
            }
        }
        val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        iframes.forEach { iframe ->
            try {
                if (iframe.contains("jetv.xyz")) {
                    Log.d("JTF", "jetv » $iframe")
                    val jetvDoc = app.get(iframe).document
                    /*val jetvIframe = fixUrlNull(jetvDoc.selectFirst("iframe")?.attr("src")) ?: return@forEach
                    Log.d("JTF", "jetvIframe » $jetvIframe")
                    loadExtractor(jetvIframe, "${mainUrl}/", subtitleCallback, callback)*/
                    val script =
                        jetvDoc.select("script").find { it.data().contains("\"sources\": [") }
                            ?.data()
                            ?: ""
                    val source = script.substringAfter("\"sources\": [").substringBefore("]")
                        .addMarks("file").addMarks("type").addMarks("label").replace("\'", "\"")
                    Log.d("JTF", "source -> $source")
                    val son: Source = objectMapper.readValue(source)
                    callback.invoke(
                        newExtractorLink(
                            source = "Jetv" + " - " + son.label,
                            name = "Jetv" + " - " + son.label,
                            url = son.file,
                            ExtractorLinkType.M3U8
                        ) {
                            this.quality = Qualities.Unknown.value
                        }
                    )

                } else if (iframe.contains("d2rs.com")) {
                    val doc = app.get(iframe).text
                    val parameter =
                        doc.substringAfter("form.append(\"q\", \"").substringBefore("\");");
                    val d2List = app.post(
                        "https://d2rs.com/zeus/api.php",
                        data = mapOf("q" to parameter),
                        referer = iframe
                    ).body.string()
                    val son: List<Source> = objectMapper.readValue(d2List)
                    for (it in son) {
                        val type = if (it.type == "video/mp4") {
                            ExtractorLinkType.VIDEO
                        } else ExtractorLinkType.M3U8
                        try {
                            callback.invoke(
                                newExtractorLink(
                                    source = "D2rs" + " - " + it.label,
                                    name = "D2rs" + " - " + it.label,
                                    url = "https://d2rs.com/zeus/" + it.file,
                                    type
                                ) {
                                    this.quality = getQualityFromName(it.label)
                                }
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                            continue
                        }
                    }
                } else if (iframe.contains("videolar.biz")) {
                    val doc = app.get(iframe, referer = mainUrl).document
                    val script =
                        doc.select("script").find { it.data().contains("eval(function(p,a,c,k,e,") }
                            ?.data() ?: ""
                    val unpacked = JsUnpacker(script).unpack()
                    val kaken =
                        unpacked?.substringAfter("window.kaken=\"")?.substringBefore("\";") ?: ""
                    val urls = "https://s2.videolar.biz/api/"
                    val mediaType = "text/plain".toMediaType()
                    val requestBody = kaken.toRequestBody(mediaType)
                    val doc2 = app.post(urls, requestBody = requestBody).body.string()
                    val son: VidBiz = objectMapper.readValue(doc2)
                    if (son.status == "ok") {
                        for (it in son.sources) {
                            try {
                                callback.invoke(
                                    newExtractorLink(
                                        source = "VidBiz" + " - " + it.label,
                                        name = "VidBiz" + " - " + it.label,
                                        url = it.file,
                                        ExtractorLinkType.M3U8
                                    ) {
                                        this.quality = getQualityFromName(it.label)
                                    }
                                )
                            } catch (e: Exception) {
                                e.printStackTrace()
                                continue
                            }
                        }
                    }
                } else {
                    loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
                }
            } catch (e: Exception) {
                return@forEach
            }
        }
        return true
    }

    private fun String.addMarks(str: String): String {
        return this.replace(Regex("\"?$str\"?"), "\"$str\"")
    }
}
