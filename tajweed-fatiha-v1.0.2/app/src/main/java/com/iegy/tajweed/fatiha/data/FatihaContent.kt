package com.iegy.tajweed.fatiha.data

data class MaddEvent(
    val id: String,
    val wordIndex: Int,
    val phonemeIndex: Int,
    val targetHarakat: Int,
    val nameAr: String
)

data class AyahSpec(
    val number: Int,
    val text: String,
    val words: List<String>,
    val phonemes: List<List<String>>,
    val madd: List<MaddEvent>,
    val rules: List<String>
)

object FatihaContent {
    val ayat: List<AyahSpec> = listOf(
        AyahSpec(
            1,
            "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
            listOf("بِسْمِ", "اللَّهِ", "الرَّحْمَٰنِ", "الرَّحِيمِ"),
            listOf(
                listOf("b","i","s","m"),
                listOf("a","l","lˤ","aː","h"),
                listOf("a","r","r","a","ħ","m","aː","n"),
                listOf("a","r","r","a","ħ","iː","m")
            ),
            listOf(
                MaddEvent("MADD-TABII-1-2", 1, 3, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-1-3", 2, 6, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-1-4", 3, 5, 2, "مد طبيعي")
            ),
            listOf("لام لفظ الجلالة", "لام شمسية", "مد طبيعي")
        ),
        AyahSpec(
            2,
            "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ",
            listOf("الْحَمْدُ", "لِلَّهِ", "رَبِّ", "الْعَالَمِينَ"),
            listOf(
                listOf("a","l","ħ","a","m","d"),
                listOf("l","i","l","lˤ","aː","h"),
                listOf("r","a","b","b"),
                listOf("a","l","ʕ","aː","l","a","m","iː","n")
            ),
            listOf(
                MaddEvent("MADD-TABII-2-2", 1, 4, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-2-4A", 3, 3, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-2-4B", 3, 7, 2, "مد طبيعي")
            ),
            listOf("لام قمرية", "تفخيم الراء", "تشديد الباء", "مد طبيعي")
        ),
        AyahSpec(
            3,
            "الرَّحْمَٰنِ الرَّحِيمِ",
            listOf("الرَّحْمَٰنِ", "الرَّحِيمِ"),
            listOf(
                listOf("a","r","r","a","ħ","m","aː","n"),
                listOf("a","r","r","a","ħ","iː","m")
            ),
            listOf(
                MaddEvent("MADD-TABII-3-1", 0, 6, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-3-2", 1, 5, 2, "مد طبيعي")
            ),
            listOf("لام شمسية", "تفخيم الراء", "مد طبيعي")
        ),
        AyahSpec(
            4,
            "مَالِكِ يَوْمِ الدِّينِ",
            listOf("مَالِكِ", "يَوْمِ", "الدِّينِ"),
            listOf(
                listOf("m","aː","l","i","k"),
                listOf("j","a","w","m"),
                listOf("a","d","d","iː","n")
            ),
            listOf(
                MaddEvent("MADD-TABII-4-1", 0, 1, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-4-3", 2, 3, 2, "مد طبيعي")
            ),
            listOf("مد طبيعي", "حرف لين", "لام شمسية")
        ),
        AyahSpec(
            5,
            "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
            listOf("إِيَّاكَ", "نَعْبُدُ", "وَإِيَّاكَ", "نَسْتَعِينُ"),
            listOf(
                listOf("ʔ","i","j","j","aː","k"),
                listOf("n","a","ʕ","b","u","d"),
                listOf("w","a","ʔ","i","j","j","aː","k"),
                listOf("n","a","s","t","a","ʕ","iː","n")
            ),
            listOf(
                MaddEvent("MADD-TABII-5-1", 0, 4, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-5-3", 2, 6, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-5-4", 3, 6, 2, "مد طبيعي")
            ),
            listOf("تشديد الياء", "مد طبيعي", "تحقيق الهمزة")
        ),
        AyahSpec(
            6,
            "اهْدِنَا الصِّرَاطَ الْمُسْتَقِيمَ",
            listOf("اهْدِنَا", "الصِّرَاطَ", "الْمُسْتَقِيمَ"),
            listOf(
                listOf("i","h","d","i","n","aː"),
                listOf("a","sˤ","sˤ","i","r","aː","tˤ"),
                listOf("a","l","m","u","s","t","a","q","iː","m")
            ),
            listOf(
                MaddEvent("MADD-TABII-6-1", 0, 5, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-6-2", 1, 5, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-6-3", 2, 8, 2, "مد طبيعي")
            ),
            listOf("لام شمسية", "تفخيم الصاد والطاء", "قلقلة القاف", "مد طبيعي")
        ),
        AyahSpec(
            7,
            "صِرَاطَ الَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ الْمَغْضُوبِ عَلَيْهِمْ وَلَا الضَّالِّينَ",
            listOf("صِرَاطَ", "الَّذِينَ", "أَنْعَمْتَ", "عَلَيْهِمْ", "غَيْرِ", "الْمَغْضُوبِ", "عَلَيْهِمْ", "وَلَا", "الضَّالِّينَ"),
            listOf(
                listOf("sˤ","i","r","aː","tˤ"),
                listOf("a","l","l","a","ð","iː","n"),
                listOf("ʔ","a","n","ʕ","a","m","t"),
                listOf("ʕ","a","l","a","j","h","i","m"),
                listOf("ɣ","a","j","r"),
                listOf("a","l","m","a","ɣ","dˤ","uː","b"),
                listOf("ʕ","a","l","a","j","h","i","m"),
                listOf("w","a","l","aː"),
                listOf("a","dˤ","dˤ","aː","l","l","iː","n")
            ),
            listOf(
                MaddEvent("MADD-TABII-7-1", 0, 3, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-7-2", 1, 5, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-7-6", 5, 6, 2, "مد طبيعي"),
                MaddEvent("MADD-TABII-7-8", 7, 3, 2, "مد طبيعي"),
                MaddEvent("MADD-LAZIM-7-9", 8, 3, 6, "مد لازم كلمي مثقل"),
                MaddEvent("MADD-TABII-7-9B", 8, 6, 2, "مد طبيعي")
            ),
            listOf("تفخيم الصاد والطاء", "لام شمسية", "إظهار حلقي", "إظهار شفوي", "لام قمرية", "مد طبيعي", "مد لازم كلمي مثقل")
        )
    )

    fun fullSurah(): AyahSpec {
        val words = mutableListOf<String>()
        val phones = mutableListOf<List<String>>()
        val madd = mutableListOf<MaddEvent>()
        val rules = linkedSetOf<String>()
        ayat.forEach { ayah ->
            val offset = words.size
            words += ayah.words
            phones += ayah.phonemes
            madd += ayah.madd.map { it.copy(wordIndex = it.wordIndex + offset) }
            rules += ayah.rules
        }
        return AyahSpec(
            number = 0,
            text = ayat.joinToString(" ۝ ") { it.text },
            words = words,
            phonemes = phones,
            madd = madd,
            rules = rules.toList()
        )
    }

    fun byNumber(number: Int): AyahSpec = if (number == 0) fullSurah() else ayat.first { it.number == number }
}
