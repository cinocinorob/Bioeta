package it.cinocinorob.bioeta.science

/**
 * Domande del diario, in stile WHOOP Journal: risposta esplicita Sì/No (o quantità) sul giorno appena
 * trascorso. La risposta esplicita è ciò che rende affidabili le «Scoperte»: un giorno senza risposta è
 * sconosciuto, non un «no».
 *
 * Le chiavi delle voci già presenti nelle versioni precedenti coincidono con le vecchie etichette, così lo
 * storico del diario resta valido.
 */
enum class JCat(val label: String) {
    FOOD("Cibo e bevande"),
    EVENING("Sera e sonno"),
    MIND("Mente e relazioni"),
    BODY("Corpo e recupero"),
    HEALTH("Salute"),
    SUPP("Integratori e farmaci"),
    CUSTOM("Le tue voci"),
}

enum class JKind { YES_NO, COUNT }

data class JItem(
    val key: String,
    val label: String,
    val cat: JCat,
    val kind: JKind = JKind.YES_NO,
    val unit: String? = null,
    val max: Int = 10,
    /** Perché può contare, in una riga, senza promettere effetti. */
    val why: String = "",
    val default: Boolean = false,
)

object JournalCatalog {
    val ITEMS: List<JItem> = listOf(
        // Cibo e bevande
        JItem("Alcol", "Alcol", JCat.FOOD, JKind.COUNT, "drink", 12,
            "Anche dosi moderate alzano la FC notturna e abbassano l'HRV, in modo proporzionale alla quantità.", default = true),
        JItem("Caffeina", "Caffè o altre bevande con caffeina", JCat.FOOD, JKind.COUNT, "tazze", 10,
            "La caffeina ha un'emivita di circa 5 ore: conta quanta e quando.", default = true),
        JItem("Caffè dopo le 14", "Caffeina dopo le 14", JCat.FOOD,
            why = "Nelle meta-analisi la caffeina presa nel pomeriggio riduce la durata e l'efficienza del sonno.", default = true),
        JItem("Cena tardiva", "Cena nelle 2–3 ore prima di dormire", JCat.FOOD,
            why = "La digestione attiva durante il sonno può alzare FC e temperatura notturne.", default = true),
        JItem("Pasto abbondante", "Pasto molto abbondante", JCat.FOOD),
        JItem("Dolci la sera", "Dolci o zuccheri la sera", JCat.FOOD),
        JItem("Cibo ultra-processato", "Cibo ultra-processato o fast food", JCat.FOOD),
        JItem("Digiuno 14 h", "Digiuno di 14 ore o più", JCat.FOOD),
        // Sera e sonno
        JItem("Schermi a letto", "Schermi a letto", JCat.EVENING,
            why = "Luce e contenuti stimolanti possono ritardare l'addormentamento.", default = true),
        JItem("Lavoro fino a tardi", "Lavoro fino a tardi", JCat.EVENING),
        JItem("Lettura prima di dormire", "Lettura prima di dormire", JCat.EVENING),
        JItem("Camera fresca e buia", "Camera fresca e buia", JCat.EVENING,
            why = "Una stanza fresca favorisce il calo di temperatura che accompagna il sonno profondo."),
        JItem("Letto condiviso", "Letto condiviso (partner o animale)", JCat.EVENING),
        JItem("Pisolino", "Pisolino", JCat.EVENING),
        // Mente e relazioni
        JItem("Stress", "Giornata stressante", JCat.MIND,
            why = "Lo stress psicologico riduce l'HRV e può alzare la FC a riposo la notte seguente.", default = true),
        JItem("Ansia", "Ansia o preoccupazioni", JCat.MIND),
        JItem("Meditazione", "Meditazione o respirazione lenta", JCat.MIND, default = true),
        JItem("Tempo con altri", "Tempo con amici o famiglia", JCat.MIND),
        JItem("Natura", "Tempo nella natura", JCat.MIND),
        JItem("Viaggio", "Viaggio", JCat.MIND),
        // Corpo e recupero
        JItem("Luce del mattino", "Luce del giorno al mattino (≥ 10 min all'aperto)", JCat.BODY,
            why = "La luce del mattino è il segnale principale che sincronizza l'orologio biologico.", default = true),
        JItem("Allenamento intenso", "Allenamento intenso", JCat.BODY),
        JItem("Stretching", "Stretching o mobilità", JCat.BODY),
        JItem("Camminata dopo i pasti", "Camminata dopo i pasti", JCat.BODY),
        JItem("Sauna", "Sauna", JCat.BODY),
        JItem("Freddo", "Doccia o bagno freddo", JCat.BODY),
        JItem("Dolori muscolari", "Dolori muscolari", JCat.BODY),
        // Salute
        JItem("Malattia", "Malattia o sintomi (raffreddore, febbre…)", JCat.HEALTH,
            why = "Serve anche a verificare il Radar malessere sui tuoi dati.", default = true),
        JItem("Mal di testa", "Mal di testa", JCat.HEALTH),
        JItem("Allergia", "Sintomi allergici", JCat.HEALTH),
        JItem("Antidolorifici", "Antidolorifici o antinfiammatori", JCat.HEALTH),
        JItem("Infortunio", "Infortunio o dolore", JCat.HEALTH),
        // Integratori e farmaci
        JItem("Magnesio", "Magnesio", JCat.SUPP),
        JItem("Melatonina", "Melatonina", JCat.SUPP),
        JItem("Creatina", "Creatina", JCat.SUPP),
        JItem("Omega-3", "Omega-3", JCat.SUPP),
        JItem("Vitamina D", "Vitamina D", JCat.SUPP),
    )

    val DEFAULT_KEYS: Set<String> = ITEMS.filter { it.default }.map { it.key }.toSet()

    fun byKey(key: String): JItem? = ITEMS.firstOrNull { it.key == key }

    fun custom(label: String) = JItem("custom:$label", label, JCat.CUSTOM)

    /** Voce da chiave (catalogo o personalizzata). */
    fun resolve(key: String): JItem = byKey(key) ?: if (key.startsWith("custom:")) custom(key.removePrefix("custom:")) else JItem(key, key, JCat.CUSTOM)
}

/** Risposta a una voce: true = sì, false = no, null = non risposto. */
fun JournalEntry.answer(key: String): Boolean? = when {
    key in tags -> true
    key in no -> false
    else -> null
}

/** true se la giornata ha almeno una risposta esplicita Sì/No (formato nuovo). */
val JournalEntry.hasExplicitAnswers: Boolean get() = explicit

/** Imposta la risposta (null = cancella); per le quantità aggiorna anche [JournalEntry.amounts]. */
fun JournalEntry.withAnswer(key: String, yes: Boolean?, amount: Double? = null): JournalEntry {
    val t = tags.toMutableSet(); val n = no.toMutableSet(); val a = amounts.toMutableMap()
    t.remove(key); n.remove(key); a.remove(key)
    when (yes) {
        true -> { t += key; if (amount != null) a[key] = amount }
        false -> n += key
        null -> {}
    }
    return copy(tags = t, no = n, amounts = a, explicit = true)
}
