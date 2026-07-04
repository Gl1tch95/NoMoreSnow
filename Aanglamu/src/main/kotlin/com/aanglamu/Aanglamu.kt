package com.nomoresnow

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Aanglamu : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AanglamuProvider())
        registerExtractorAPI(Vidking())
    }
}
