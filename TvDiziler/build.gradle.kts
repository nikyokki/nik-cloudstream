version = 6

cloudstream {
    authors     = listOf("nikyokki")
    language    = "tr"
    description = "Ücretsiz yerli dizi izleme sitesi en popüler dizileri tek parça hd kalitesiyle ve reklamsız bedava olarak buradan tv dizileri izleyin."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=tvdiziler.cc&sz=%size%"
}