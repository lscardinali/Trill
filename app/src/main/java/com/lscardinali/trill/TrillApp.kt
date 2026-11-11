package com.lscardinali.trill

import android.app.Application
import com.lscardinali.trill.data.OkDownloader
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class TrillApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkDownloader(http), Localization.DEFAULT, ContentCountry.DEFAULT)
    }

    companion object {
        val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }
    }
}
