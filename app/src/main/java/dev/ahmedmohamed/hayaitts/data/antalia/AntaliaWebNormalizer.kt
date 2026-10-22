package dev.ahmedmohamed.hayaitts.data.antalia

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONTokener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs the upstream normalize.js unchanged on Android's Chromium JS engine.
 * This is necessary because its Unicode property escapes/lookbehinds are not
 * supported by legacy Android Javascript interpreters.
 *
 * Assets must be bundled at build time: antalia/normalize.js and 4 dictionaries.
 * Do not call synchronously from the Android UI thread.
 */
internal class AntaliaWebNormalizer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())

    fun normalize(raw: String): String {
        require(Looper.myLooper() != Looper.getMainLooper()) {
            "WebView normalization may not block the UI thread"
        }
        val ready = CountDownLatch(1)
        var result: Result<String> = Result.failure(IllegalStateException("Not executed"))
        main.post {
            val web = WebView(context.applicationContext)
            try {
                val js = context.assets.open("antalia/normalize.js").bufferedReader().use { it.readText() }
                val dictionaryNames = listOf("foreign_dict", "foreign_parts", "known_words", "english_i")
                val dict = dictionaryNames.associateWith { name ->
                    context.assets.open("antalia/$name.json").bufferedReader().use { it.readText() }
                }
                val boot = js + "\nAntaliaText.setData({foreign: " + dict.getValue("foreign_dict") +
                    ", parts: " + dict.getValue("foreign_parts") +
                    ", known: " + dict.getValue("known_words") +
                    ", englishI: " + dict.getValue("english_i") + "});"
                web.evaluateJavascript(boot) {
                    val arg = org.json.JSONObject.quote(raw)
                    web.evaluateJavascript("AntaliaText.toModelText($arg)") { json ->
                        result = runCatching {
                            JSONTokener(json).nextValue() as String
                        }
                        web.destroy()
                        ready.countDown()
                    }
                }
            } catch (e: Throwable) {
                result = Result.failure(e)
                web.destroy()
                ready.countDown()
            }
        }
        check(ready.await(45, TimeUnit.SECONDS)) { "Antalia normalizer timed out" }
        return result.getOrThrow()
    }
}
