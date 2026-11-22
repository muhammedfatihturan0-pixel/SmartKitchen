package com.example.domain

import java.util.Locale

object UsernameValidator {

    private val trLocale: Locale = Locale.forLanguageTag("tr")

    val defaultTakenUsernames: Set<String> = setOf(
        "atıksavaşçısı_aslı", "şef_melih_06", "sıfıratıkesra", "ecochef_ebru",
        "zerowaste_kaan", "mutfakcanavarı", "mutfakkahramanı", "admin", "administrator",
        "sistem", "system", "root", "support", "destek", "null", "anonymous", "misafir"
    )

    private val forbiddenWholeWords = listOf(
        "küfür", "aptal", "mal", "salak", "admin", "hacker", "system", "root", "administrator"
    )

    private val validRegex = Regex("^[a-zA-Z0-9çğışöüÇĞİŞÖÜ_-]+( [a-zA-Z0-9çğışöüÇĞİŞÖÜ_-]+)*$")

    /**
     * Validates username during typing (empty input returns null so initial screen has no red banner).
     */
    fun validateForDisplay(
        input: String,
        takenUsernames: Set<String> = defaultTakenUsernames,
        currentUsername: String? = null
    ): String? {
        if (input.isEmpty()) return null
        return validateInternal(input, takenUsernames, currentUsername)
    }

    /**
     * Validates username on submit (empty/blank input is rejected).
     */
    fun validateForSubmit(
        input: String,
        takenUsernames: Set<String> = defaultTakenUsernames,
        currentUsername: String? = null
    ): String? {
        if (input.isBlank()) {
            return "Kullanıcı adı boş bırakılamaz."
        }
        return validateInternal(input, takenUsernames, currentUsername)
    }

    private fun validateInternal(
        input: String,
        takenUsernames: Set<String>,
        currentUsername: String?
    ): String? {
        val trimmed = input.trim()
        if (input.startsWith(" ") || input.endsWith(" ")) {
            return "Kullanıcı adı başında veya sonunda boşluk olamaz."
        }
        if (trimmed.length < 3) {
            return "Kullanıcı adı en az 3 karakter olmalıdır."
        }
        if (trimmed.length > 20) {
            return "Kullanıcı adı en fazla 20 karakter olabilir."
        }
        if (!validRegex.matches(trimmed)) {
            return "Özel semboller (@, #, $, %, vb.) kullanılamaz. Sadece harf, rakam ve tire/alt çizgi girebilirsiniz."
        }

        val lowerInput = trimmed.lowercase(trLocale)
        val lowerCurrent = currentUsername?.trim()?.lowercase(trLocale)
        val isKeepingOwnName = lowerCurrent != null && lowerInput == lowerCurrent

        if (!isKeepingOwnName && takenUsernames.contains(lowerInput)) {
            return "Bu kullanıcı adı zaten alınmış. Lütfen benzersiz bir ad seçin."
        }

        // Split on whitespace, underscore, hyphen so "Kemal" / "İsmail" / "Cemal" are NOT blocked by "mal"!
        val tokens = lowerInput.split(Regex("[\\s_-]+")).filter { it.isNotBlank() }
        if (tokens.any { it in forbiddenWholeWords }) {
            return "Lütfen topluluk kurallarına uygun bir kullanıcı adı seçin."
        }

        return null
    }
}
