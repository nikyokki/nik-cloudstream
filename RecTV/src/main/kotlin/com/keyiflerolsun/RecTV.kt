// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.
// ! https://github.com/Amiqo09/Diziyou-Cloudstream

package com.keyiflerolsun

import android.util.Log
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import okhttp3.Interceptor

class RecTV : MainAPI() {
    override var mainUrl = "https://m.prectv50.sbs"
    override var name = "RecTV"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.Live, TvType.TvSeries)

    private val swKey = "4F5A9C3D9A86FA54EACEDDD635185/64f9535b-bd2e-4483-b234-89060b1e631c"

    override val mainPage = mainPageOf(
        "${mainUrl}/api/channel/by/filtres/0/0/SAYFA/${swKey}/" to "Canlı",
        "${mainUrl}/api/movie/by/filtres/0/created/SAYFA/${swKey}/" to "Son Filmler",
        "${mainUrl}/api/serie/by/filtres/0/created/SAYFA/${swKey}/" to "Son Diziler",
        "${mainUrl}/api/movie/by/filtres/14/created/SAYFA/${swKey}/" to "Aile",
        "${mainUrl}/api/movie/by/filtres/1/created/SAYFA/${swKey}/" to "Aksiyon",
        "${mainUrl}/api/movie/by/filtres/13/created/SAYFA/${swKey}/" to "Animasyon",
        "${mainUrl}/api/movie/by/filtres/19/created/SAYFA/${swKey}/" to "Belgesel",
        "${mainUrl}/api/movie/by/filtres/4/created/SAYFA/${swKey}/" to "Bilim Kurgu",
        "${mainUrl}/api/movie/by/filtres/2/created/SAYFA/${swKey}/" to "Dram",
        "${mainUrl}/api/movie/by/filtres/10/created/SAYFA/${swKey}/" to "Fantastik",
        "${mainUrl}/api/movie/by/filtres/3/created/SAYFA/${swKey}/" to "Komedi",
        "${mainUrl}/api/movie/by/filtres/8/created/SAYFA/${swKey}/" to "Korku",
        "${mainUrl}/api/movie/by/filtres/17/created/SAYFA/${swKey}/" to "Macera",
        "${mainUrl}/api/movie/by/filtres/5/created/SAYFA/${swKey}/" to "Romantik"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        @Suppress("NAME_SHADOWING") val page = page - 1

        val url = request.data.replace("SAYFA", "$page")
        val home = app.get(url, headers = mapOf("user-agent" to "Dart/3.7 (dart:io)"))

        val movies = AppUtils.tryParseJson<List<RecItem>>(home.text)!!.map { item ->
            val toDict = jacksonObjectMapper().writeValueAsString(item)

            if (item.label != "CANLI" && item.label != "Canlı") {
                newMovieSearchResponse(item.title, toDict, TvType.Movie) {
                    this.posterUrl = item.image
                    this.score = Score.from10(item.imdb)
                }
            } else {
                newLiveSearchResponse(item.title, toDict, TvType.Live) {
                    this.posterUrl = item.image
                    this.score = Score.from10(item.imdb)
                }
            }
        }

        return newHomePageResponse(request.name, movies)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val home = app.get(
            "${mainUrl}/api/search/${query}/${swKey}/",
            headers = mapOf("user-agent" to "Dart/3.7 (dart:io)")
        )
        val veriler = AppUtils.tryParseJson<RecSearch>(home.text)

        val sonuclar = mutableListOf<SearchResponse>()

        veriler?.channels?.let { channels ->
            for (item in channels) {
                val toDict = jacksonObjectMapper().writeValueAsString(item)

                sonuclar.add(newMovieSearchResponse(item.title, toDict, TvType.Movie) {
                    this.posterUrl = item.image
                    this.score = Score.from10(item.imdb)
                })
            }
        }

        veriler?.posters?.let { posters ->
            for (item in posters) {
                val toDict = jacksonObjectMapper().writeValueAsString(item)

                sonuclar.add(
                    newMovieSearchResponse(
                        item.title,
                        toDict,
                        TvType.Movie
                    ) { this.posterUrl = item.image })
            }
        }

        return sonuclar
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val veri = AppUtils.tryParseJson<RecItem>(url) ?: return null

        if (veri.type == "serie") {
            val diziReq = app.get(
                "${mainUrl}/api/season/by/serie/${veri.id}/${swKey}/",
                headers = mapOf("user-agent" to "Dart/3.7 (dart:io)")
            )
            val sezonlar = AppUtils.tryParseJson<List<RecDizi>>(diziReq.text) ?: return null

            val episodes = mutableMapOf<DubStatus, MutableList<Episode>>()

            val numberRegex = Regex("\\d+")

            for (sezon in sezonlar) {
                val seasonDubStatus = if (sezon.title.contains(
                        "altyazı",
                        ignoreCase = true
                    )
                ) DubStatus.Subbed else if (sezon.title.contains(
                        "dublaj",
                        ignoreCase = true
                    )
                ) DubStatus.Dubbed else DubStatus.None
                for (bolum in sezon.episodes) {
                    episodes.getOrPut(seasonDubStatus) { mutableListOf() }
                        .add(newEpisode(bolum.sources.first().url) {
                            this.name = bolum.title
                            this.season = numberRegex.find(sezon.title)?.value?.toIntOrNull()
                            this.episode = numberRegex.find(bolum.title)?.value?.toIntOrNull()
                            this.description = sezon.title.substringAfter(".S ")
                            this.posterUrl = veri.image
                        })
                }
            }

            return newAnimeLoadResponse(
                name = veri.title,
                url = url,
                type = TvType.TvSeries,
                comingSoonIfNone = false
            ) {
                this.episodes = episodes.mapValues { it.value.toList() }.toMutableMap()
                this.posterUrl = veri.image
                this.plot = veri.description
                this.year = veri.year
                this.tags = veri.genres?.map { it.title }
                this.score = Score.from10(veri.imdb)
            }
        }

        return if (veri.label != "CANLI" && veri.label != "Canlı") {
            newMovieLoadResponse(veri.title, url, TvType.Movie, url) {
                this.posterUrl = veri.image
                this.plot = veri.description
                this.year = veri.year
                this.tags = veri.genres?.map { it.title }
                this.score = Score.from10(veri.imdb)
            }
        } else {
            newLiveStreamLoadResponse(veri.title, url, url) {
                this.posterUrl = veri.image
                this.plot = veri.description
                this.tags = veri.genres?.map { it.title }
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.startsWith("http")) {
            Log.d("RCTV", "data » $data")
            callback.invoke(
                newExtractorLink(
                    source = this.name,
                    name = this.name,
                    url = data,
                    type = INFER_TYPE
                ) {
                    this.referer = "https://twitter.com/"
                    this.quality = Qualities.Unknown.value
                }
            )
            return true
        }

        val veri = AppUtils.tryParseJson<RecItem>(data) ?: return false

        for (source in veri.sources) {
            Log.d("RCTV", "source » $source")
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = "${this.name} - ${source.type}",
                    url = source.url,
                    referer = "https://twitter.com/",
                    quality = Qualities.Unknown.value,
                    type = if (source.type == "mp4") ExtractorLinkType.VIDEO else ExtractorLinkType.M3U8
                )
            )
        }

        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        val interceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val modifiedRequest = originalRequest.newBuilder()
                .removeHeader("If-None-Match")
                .header("User-Agent", "googleusercontent")
                .build()
            chain.proceed(modifiedRequest)
        }
        return interceptor
    }
}

