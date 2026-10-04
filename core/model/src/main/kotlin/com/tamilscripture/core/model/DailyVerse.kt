package com.tamilscripture.core.model

import java.time.LocalDate

/** Verses of the day, one per day of the year in turn (home screen and widget). */
object DailyVerse {
    private val IDS = listOf(
        "PSA.119.105", "JHN.3.16", "PSA.23.1", "ISA.40.31", "PHP.4.13", "ROM.8.28", "PRO.3.5", "MAT.11.28",
        "JER.29.11", "PSA.46.1", "ROM.12.2", "2TI.1.7", "JHN.14.6", "PSA.27.1", "ISA.41.10", "MAT.6.33",
        "GAL.5.22", "1JN.4.19", "PSA.121.1", "LAM.3.22", "EPH.2.8", "HEB.11.1", "1CO.13.4", "JOS.1.9",
        "PSA.37.4", "MIC.6.8", "JHN.15.5", "ROM.5.8", "2CO.5.17", "PSA.139.14", "REV.21.4",
    )

    fun forDate(date: LocalDate = LocalDate.now()): VerseId = VerseId.parse(IDS[(date.dayOfYear - 1) % IDS.size])!!
}
