version = 8

cloudstream {
    authors     = listOf("nikyokki")
    language    = "tr"
    description = "Full hd film izleme keyfini hdfilmcehennemi2 ile yaşayın. Türkiyenin en güncel yüksek hd kalitede film izleme sitesi."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.hdfilmcehennemi2.nl&sz=%size%"
}