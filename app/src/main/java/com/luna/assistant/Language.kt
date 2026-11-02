package com.luna.assistant

import android.content.Context
import java.text.Normalizer
import java.util.Locale

enum class LunaLanguage(val displayName: String, val speechTag: String, val locale: Locale) {
    ENGLISH("English", "en-NG", Locale("en", "NG")),
    YORUBA("Yorùbá", "yo-NG", Locale("yo", "NG")),
    IGBO("Igbo", "ig-NG", Locale("ig", "NG")),
    HAUSA("Hausa", "ha-NG", Locale("ha", "NG")),
}

object Prefs {
    private fun prefs(c: Context) = c.getSharedPreferences("luna", Context.MODE_PRIVATE)

    fun language(c: Context): LunaLanguage =
        runCatching { LunaLanguage.valueOf(prefs(c).getString("lang", null) ?: "ENGLISH") }
            .getOrDefault(LunaLanguage.ENGLISH)

    fun setLanguage(c: Context, lang: LunaLanguage) =
        prefs(c).edit().putString("lang", lang.name).apply()
}

/** Lower-cases text and removes tone marks, so "Ṣí" and "si" match the same command. */
object TextNorm {
    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{Mn}+"), "")
            .replace("ƙ", "k").replace("ɗ", "d").replace("ɓ", "b").replace("ƴ", "y")
            .replace("’", "'")
            .replace(Regex("[^a-z0-9' ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun squash(s: String) = normalize(s).replace(" ", "")
}

/** Everything Luna can say back. */
enum class Reply {
    OPENING, NO_APP, CALLING, NO_CONTACT, SENT, NEED_MESSAGE, CAMERA,
    NOT_UNDERSTOOD, NO_PERMISSION, FAILED, NO_SPEECH_SERVICE
}

object Phrases {
    private val table: Map<LunaLanguage, Map<Reply, String>> = mapOf(
        LunaLanguage.ENGLISH to mapOf(
            Reply.OPENING to "Opening {x}",
            Reply.NO_APP to "I couldn't find an app called {x}",
            Reply.CALLING to "Calling {x}",
            Reply.NO_CONTACT to "I couldn't find {x} in your contacts",
            Reply.SENT to "Message sent to {x}",
            Reply.NEED_MESSAGE to "Please say the message too. For example: text {x} saying I'm on my way",
            Reply.CAMERA to "Opening the camera",
            Reply.NOT_UNDERSTOOD to "Sorry, I didn't get that. Please try again.",
            Reply.NO_PERMISSION to "I need permission for that. Please open Luna and tap Grant permissions.",
            Reply.FAILED to "Sorry, something went wrong.",
            Reply.NO_SPEECH_SERVICE to "Speech recognition isn't available on this phone. Please install or update the Google app.",
        ),
        // NOTE: Yoruba, Igbo and Hausa phrases are a first draft and should be checked by native speakers.
        LunaLanguage.YORUBA to mapOf(
            Reply.OPENING to "Mo n ṣí {x}",
            Reply.NO_APP to "Mi ò rí app tí ń jẹ́ {x}",
            Reply.CALLING to "Mo n pe {x}",
            Reply.NO_CONTACT to "Mi ò rí {x} nínú àwọn nọ́mbà rẹ",
            Reply.SENT to "Mo ti fi ọ̀rọ̀ ránṣẹ́ sí {x}",
            Reply.NEED_MESSAGE to "Kí ni kí ọ̀rọ̀ náà sọ? Sọ ọ́ pẹ̀lú orúkọ náà.",
            Reply.CAMERA to "Mo n ṣí kámẹ́rà",
            Reply.NOT_UNDERSTOOD to "Má bínú, mi ò gbọ́ ọ yé. Jọ̀wọ́ tún sọ.",
            Reply.NO_PERMISSION to "Mo nílò àṣẹ fún èyí. Jọ̀wọ́ ṣí Luna kí o fún mi ní àṣẹ.",
            Reply.FAILED to "Má bínú, nǹkan kan ò lọ dáadáa.",
        ),
        LunaLanguage.IGBO to mapOf(
            Reply.OPENING to "Ana m emepe {x}",
            Reply.NO_APP to "Ahụghị m app aha ya bụ {x}",
            Reply.CALLING to "Ana m akpọ {x}",
            Reply.NO_CONTACT to "Ahụghị m {x} na kọntaktị gị",
            Reply.SENT to "Ezigala m ozi nye {x}",
            Reply.NEED_MESSAGE to "Gịnị ka ozi ahụ ga-ekwu? Kwuo ya na aha ahụ.",
            Reply.CAMERA to "Ana m emepe kamera",
            Reply.NOT_UNDERSTOOD to "Ndo, aghọtaghị m. Biko kwuo ọzọ.",
            Reply.NO_PERMISSION to "Achọrọ m ikike maka nke a. Biko mepee Luna nye m ikike.",
            Reply.FAILED to "Ndo, ihe adịghị mma mere.",
        ),
        LunaLanguage.HAUSA to mapOf(
            Reply.OPENING to "Ina buɗe {x}",
            Reply.NO_APP to "Ban sami app mai suna {x} ba",
            Reply.CALLING to "Ina kiran {x}",
            Reply.NO_CONTACT to "Ban sami {x} a cikin lambobinka ba",
            Reply.SENT to "Na aika saƙo zuwa ga {x}",
            Reply.NEED_MESSAGE to "Me saƙon zai ce? Faɗa shi tare da sunan.",
            Reply.CAMERA to "Ina buɗe kyamara",
            Reply.NOT_UNDERSTOOD to "Yi haƙuri, ban gane ba. Don Allah sake faɗa.",
            Reply.NO_PERMISSION to "Ina buƙatar izini don wannan. Don Allah buɗe Luna ka ba ni izini.",
            Reply.FAILED to "Yi haƙuri, wani abu bai yi daidai ba.",
        ),
    )

    fun get(lang: LunaLanguage, reply: Reply, arg: String = ""): String {
        val text = table[lang]?.get(reply) ?: table.getValue(LunaLanguage.ENGLISH).getValue(reply)
        return text.replace("{x}", arg)
    }
}
