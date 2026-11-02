package com.luna.assistant

sealed class Command {
    data class OpenApp(val name: String) : Command()
    data class Call(val name: String) : Command()
    data class SendText(val name: String, val message: String) : Command()
    data class NeedMessage(val name: String) : Command()
    object Camera : Command()
    object Unknown : Command()
}

/** Words that start each kind of command, written without tone marks. */
private data class Vocab(
    val open: List<String>,
    val call: List<String>,
    val textPrefix: List<String>,
    val textSep: List<String>,
)

object CommandParser {

    // Yoruba, Igbo and Hausa words are a first draft — have native speakers check and extend them.
    private val vocab = mapOf(
        LunaLanguage.ENGLISH to Vocab(
            open = listOf("open", "launch", "start", "open up", "go to"),
            call = listOf("call", "phone", "dial", "ring"),
            textPrefix = listOf(
                "send a text to", "send a message to", "send text to", "send message to",
                "send an sms to", "text", "message", "sms",
            ),
            textSep = listOf("saying", "that says", "and say", "and tell them", "tell them", "say"),
        ),
        LunaLanguage.YORUBA to Vocab(
            open = listOf("si", "sii", "si app"),
            call = listOf("pe", "pe fun mi"),
            textPrefix = listOf("fi oro ranse si", "fi atejise ranse si", "ko oro si", "te oro si"),
            textSep = listOf("so pe", "wipe", "pe"),
        ),
        LunaLanguage.IGBO to Vocab(
            open = listOf("mepee", "mepe", "meghee", "meghe"),
            call = listOf("kpoo", "kpo", "kporo"),
            textPrefix = listOf("ziga ozi nye", "zigara ozi", "zigara", "zitere", "dee ozi nye"),
            textSep = listOf("kwuo si", "si", "kwuo"),
        ),
        LunaLanguage.HAUSA to Vocab(
            open = listOf("bude", "bude min", "bude mini"),
            call = listOf("kira", "kirawo", "kira min"),
            textPrefix = listOf(
                "aika sako zuwa ga", "aika sako ga", "aika sako zuwa", "aika wa",
                "tura sako zuwa ga", "tura sako ga",
            ),
            textSep = listOf("ka ce", "cewa", "ce"),
        ),
    )

    private val leadingFillers = listOf(
        "hey luna", "hi luna", "hello luna", "ok luna", "luna", "please", "abeg", "jowo", "e jo",
        "biko", "don allah", "can you", "could you", "i want you to",
    )
    private val trailingFillers = listOf("please", "for me", "abeg", "jare", "biko", "now")
    private val cameraWords = listOf("camera", "kamera", "kyamara", "kamara")
    private val photoStarts = listOf("take a picture", "take a photo", "take a selfie", "snap a picture")

    fun parse(raw: String, lang: LunaLanguage): Command {
        val text = stripFillers(TextNorm.normalize(raw))
        if (text.isEmpty()) return Command.Unknown

        if (photoStarts.any { text.startsWith(it) }) return Command.Camera

        // Try the chosen language first, then English (people often mix languages).
        val tables = listOfNotNull(vocab[lang], vocab[LunaLanguage.ENGLISH].takeIf { lang != LunaLanguage.ENGLISH })
        for (v in tables) parseWith(text, v)?.let { return it }
        return Command.Unknown
    }

    private fun parseWith(t: String, v: Vocab): Command? {
        val prefix = alt(v.textPrefix)
        Regex("^(?:$prefix)\\s+(.+?)\\s+(?:${alt(v.textSep)})\\s+(.+)$").find(t)?.let {
            return Command.SendText(cleanName(it.groupValues[1]), capitalise(it.groupValues[2]))
        }
        Regex("^(?:$prefix)\\s+(.+)$").find(t)?.let {
            return Command.NeedMessage(cleanName(it.groupValues[1]))
        }
        Regex("^(?:${alt(v.open)})\\s+(.+)$").find(t)?.let {
            val target = it.groupValues[1]
            return if (cameraWords.any { w -> target.contains(w) }) Command.Camera
            else Command.OpenApp(cleanApp(target))
        }
        Regex("^(?:${alt(v.call)})\\s+(.+)$").find(t)?.let {
            return Command.Call(cleanName(it.groupValues[1]))
        }
        return null
    }

    private fun alt(words: List<String>) =
        words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }

    private fun stripFillers(input: String): String {
        var s = input
        var changed = true
        while (changed) {
            changed = false
            for (f in leadingFillers) {
                if (s == f) { s = ""; changed = true }
                else if (s.startsWith("$f ")) { s = s.removePrefix("$f ").trim(); changed = true }
            }
            for (f in trailingFillers) {
                if (s.endsWith(" $f")) { s = s.removeSuffix(" $f").trim(); changed = true }
            }
        }
        return s
    }

    private fun cleanName(s: String) = s.removePrefix("my ").removeSuffix(" mi").trim()

    private fun cleanApp(s: String) =
        s.removePrefix("the ").removeSuffix(" app").removeSuffix(" application").trim()

    private fun capitalise(s: String) = s.replaceFirstChar { it.uppercase() }
}
