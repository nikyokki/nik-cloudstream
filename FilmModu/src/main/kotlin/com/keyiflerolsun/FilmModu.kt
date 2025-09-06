// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.Actor
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
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.utils.ExtractorLinkType


class FilmModu : MainAPI() {
    override var mainUrl = "https://www.filmmodu.us"
    override var name = "FilmModu"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/hd-film-kategori/4k-film-izle" to "4K",
        "${mainUrl}/hd-film-kategori/aile-filmleri" to "Aile",
        "${mainUrl}/hd-film-kategori/aksiyon" to "Aksiyon",
        "${mainUrl}/hd-film-kategori/animasyon" to "Animasyon",
        "${mainUrl}/hd-film-kategori/belgeseller" to "Belgesel",
        "${mainUrl}/hd-film-kategori/bilim-kurgu-filmleri" to "Bilim-Kurgu",
        "${mainUrl}/hd-film-kategori/dram-filmleri" to "Dram",
        "${mainUrl}/hd-film-kategori/fantastik-filmler" to "Fantastik",
        "${mainUrl}/hd-film-kategori/gerilim" to "Gerilim",
        "${mainUrl}/hd-film-kategori/gizem-filmleri" to "Gizem",
        "${mainUrl}/hd-film-kategori/hd-hint-filmleri" to "Hint Filmleri",
        "${mainUrl}/hd-film-kategori/kisa-film" to "Kısa Film",
        "${mainUrl}/hd-film-kategori/hd-komedi-filmleri" to "Komedi",
        "${mainUrl}/hd-film-kategori/komedi" to "Komedi",
        "${mainUrl}/hd-film-kategori/korku-filmleri" to "Korku",
        "${mainUrl}/hd-film-kategori/kult-filmler-izle" to "Kült Filmler",
        "${mainUrl}/hd-film-kategori/macera-filmleri" to "Macera",
        "${mainUrl}/hd-film-kategori/muzik" to "Müzik",
        "${mainUrl}/hd-film-kategori/odullu-filmler-izle" to "Oscar Ödüllü Filmler",
        "${mainUrl}/hd-film-kategori/romantik-filmler" to "Romantik",
        "${mainUrl}/hd-film-kategori/savas" to "Savaş",
        "${mainUrl}/hd-film-kategori/savas-filmleri" to "Savaş",
        "${mainUrl}/hd-film-kategori/stand-up" to "Stand Up",
        "${mainUrl}/hd-film-kategori/suc-filmleri" to "Suç",
        "${mainUrl}/hd-film-kategori/tarih" to "Tarih",
        "${mainUrl}/hd-film-kategori/tavsiye-filmler" to "Tavsiye Filmler",
        "${mainUrl}/hd-film-kategori/tv-film" to "TV film",
        "${mainUrl}/hd-film-kategori/vahsi-bati-filmleri" to "Vahşi Batı",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}?page=${page}").document
        val home = document.select("div.movie").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title = this.selectFirst("a")?.text() ?: return null
        val href = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("picture img")?.attr("data-src"))
        val score = this.selectFirst("span.imdb-rating")?.text()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.score = Score.from10(score)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/film-ara?term=${query}").document

        return document.select("div.movie").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val orgTitle = document.selectFirst("div.titles h1")?.text()?.trim() ?: return null
        val altTitle = document.selectFirst("div.titles h2")?.text()?.trim() ?: ""
        val title = if (altTitle.isNotEmpty()) "$orgTitle - $altTitle" else orgTitle
        val poster = fixUrlNull(document.selectFirst("img.img-responsive")?.attr("src"))
        val description = document.selectFirst("p[itemprop='description']")?.text()?.trim()
        val year =
            document.selectFirst("span[itemprop='dateCreated']")?.text()?.trim()?.toIntOrNull()
        val tags = document.select("div.description a[href*='-kategori/']").map { it.text() }
        val rating =
            document.selectFirst("div.description p")?.ownText()?.split(" ")?.last()?.trim()
        val actors = document.select("div.description a[href*='-oyuncu-']")
            .map { Actor(it.selectFirst("span")!!.text()) }
        val trailer = document.selectFirst("div.container iframe")?.attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.score = Score.from10(rating)
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean, // Parametre isCasting olarak kaldı
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("FLMMD", "Başlatılıyor - loadLinks için data: $data")
        val document = app.get(data).document

        val alternates = document.select("div.alternates a")
        if (alternates.isEmpty()) {
            Log.w("FLMMD", "Alternatif bağlantılar bulunamadı! Sayfadaki 'div.alternates a' elementi boş.")
            return false
        }

        alternates.forEach { altLinkElement ->
            val altLink = fixUrlNull(altLinkElement.attr("href"))
            val altName = altLinkElement.text()

            if (altLink == null || altName.contains("Fragman", true)) {
                Log.d("FLMMD", "Fragman linki veya geçersiz link. Atlanıyor. Link: $altLink, Name: $altName")
                return@forEach
            }

            Log.d("FLMMD", "Alternatif link bulundu: $altName, URL: $altLink")

            try {
                val altReq = app.get(
                    altLink,
                    referer = data // Ana sayfa URL'sini Referer olarak ekle
                )
                val altText = altReq.text

                val vidId = Regex("""var videoId = '(\d+)';""").find(altText)?.groupValues?.getOrNull(1)
                val vidType = Regex("""var videoType = '(\w+)';""").find(altText)?.groupValues?.getOrNull(1)

                if (vidId.isNullOrEmpty() || vidType.isNullOrEmpty()) {
                    Log.e("FLMMD", "videoId ($vidId) veya videoType ($vidType) bulunamadı. altReq.text'in tamamı (ilk 500 char): ${altText.substring(0, minOf(altText.length, 500))}")
                    return@forEach
                }

                Log.d("FLMMD", "Çekilen videoId: $vidId, videoType: $vidType")

                val sourceUrl = "${mainUrl}/get-source?movie_id=${vidId}&type=${vidType}"
                Log.d("FLMMD", "get-source isteği atılıyor: $sourceUrl")

                val vidReqRaw = app.get(
                    sourceUrl,
                    referer = altLink // Buraya alternatif linki Referer olarak ekle
                )

                if (vidReqRaw.code != 200) {
                    Log.e("FLMMD", "get-source HTTP hata kodu: ${vidReqRaw.code}. Yanıt: ${vidReqRaw.text}")
                    return@forEach
                }

                Log.d("FLMMD", "get-source ham cevap (ilk 500 char): ${vidReqRaw.text.substring(0, minOf(vidReqRaw.text.length, 500))}")

                val vidReq = vidReqRaw.parsedSafe<GetSource>()

                if (vidReq == null) {
                    Log.e("FLMMD", "GetSource objesi null döndü. JSON ayrıştırma başarısız. Ham cevap: ${vidReqRaw.text.substring(0, minOf(vidReqRaw.text.length, 500))}")
                    return@forEach
                }

                vidReq.subtitle?.let { subPath ->
                    val fullSubUrl = fixUrl("${mainUrl}${subPath}") // Tam URL'yi fixUrl ile oluştur
                    subtitleCallback(SubtitleFile(altName, fullSubUrl))
                    Log.d("FLMMD", "Altyazı bulundu: $fullSubUrl")
                } ?: Log.d("FLMMD", "Altyazı bulunamadı.")

                vidReq.sources?.forEach { source ->
                    callback.invoke(
                        newExtractorLink(
                            source = source.src, // BURADA DÜZELTME YAPILDI: source.src kullanıldı
                            name = "FilmModu - $altName", // Kaynak için görünen isim
                            url = fixUrl(source.src), // URL parametresi olarak yine source.src
                            type = ExtractorLinkType.M3U8 // Orijinal kodunuzdaki gibi INFER_TYPE
                        ) {
                            this.referer = altLink // Referer, alternatif linkin kendisi
                            this.quality = getQualityFromName(source.label) // Kaliteyi atama
                        }
                    )
                    Log.d("FLMMD", "Video kaynağı eklendi: Source Name: FilmModu - ${altName}, URL: ${source.src}, Label: ${source.label}")
                } ?: Log.w("FLMMD", "Video kaynakları (sources) boş veya null. vidReq: $vidReq")

            } catch (e: Exception) {
                Log.e("FLMMD", "Alternatif link işleme hatası: $altLink, Hata: ${e.message}", e) // Hata nesnesini de logluyoruz
            }
        }
        Log.d("FLMMD", "loadLinks fonksiyonu tamamlandı.")
        return true
    }
}