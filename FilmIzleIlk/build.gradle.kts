version = 3

cloudstream {
    authors     = listOf("nikyokki")
    language    = "tr"
    description = "Filmizleilk.com Yerli ve Yabancı en son Film ve Dizileri Full HD 1080p Bluray Tek Part Full izle"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.filmizleilk.com&sz=%size%"
}
