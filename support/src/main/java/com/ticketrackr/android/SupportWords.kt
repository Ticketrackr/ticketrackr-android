package com.ticketrackr.android

import java.util.Locale

/** The SDK's own few words, in the support page's languages (sdks/protocol, section 6). */
data class SupportWords(
    val loading: String,
    val failed: String,
    val retry: String,
    val help: String,
    val back: String,
    val unread: String,
    val downloading: String,
) {
    companion object {
        private val ALL = mapOf(
            "en" to SupportWords("Loading support…", "Support couldn't open.", "Try again", "Help", "Back", "unread", "Downloading…"),
            "es" to SupportWords("Cargando soporte…", "No se pudo abrir el soporte.", "Reintentar", "Ayuda", "Volver", "sin leer", "Descargando…"),
            "fr" to SupportWords("Chargement du support…", "Le support n'a pas pu s'ouvrir.", "Réessayer", "Aide", "Retour", "non lus", "Téléchargement…"),
            "de" to SupportWords("Support wird geladen…", "Der Support konnte nicht geöffnet werden.", "Erneut versuchen", "Hilfe", "Zurück", "ungelesen", "Wird heruntergeladen…"),
            "pt" to SupportWords("Carregando o suporte…", "Não foi possível abrir o suporte.", "Tentar de novo", "Ajuda", "Voltar", "não lidas", "Baixando…"),
        )

        /** The words for [language] (`es`, `pt-BR`…), or the device's language when none is given; English otherwise. */
        @JvmStatic
        fun forLanguage(language: String?): SupportWords {
            val code = (language ?: Locale.getDefault().language).lowercase().split('-', '_').first()
            return ALL[code] ?: ALL.getValue("en")
        }
    }
}
