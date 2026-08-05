/*
 * Copyright (C) 2026 NgocThanhGL
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * ── About this file ──────────────────────────────────────────────
 * AlgorithmicSinhala is a phonetic (Helakuru-style / "Singlish") transliteration
 * composer for Sinhala. Instead of a JSON lookup table, it parses the Latin
 * buffer left-to-right into consonant + vowel syllables and emits the matching
 * Sinhala Unicode codepoints, the same "recompute the whole word every
 * keystroke" strategy AlgorithmicTelex uses for Vietnamese.
 *
 * Because Sinhala output is NOT Latin script (unlike Vietnamese diacritics),
 * this composer cannot recover the user's original Latin buffer directly from
 * the committed text the way AlgorithmicTelex does with toBaseForm(). Instead
 * it reconstructs an equivalent Latin buffer from the committed Sinhala word
 * via a reverse grapheme map, appends the new keystroke, and re-runs the
 * forward transliteration — then diffs the result against what's already
 * on screen.
 *
 * STATUS: v0.1 draft. Covers independent vowels, consonant+vowel-sign
 * syllables, and explicit halant (vowel-less consonants). Does NOT yet
 * handle: yansaya/rakaransaya conjuncts (ක්‍ය, ක්‍ර), ISHA/ORU digit-look
 * conjuncts, gemination edge cases, or a word-prediction dictionary. Treat
 * this as a starting skeleton, not a finished product — test heavily against
 * real Helakuru output before shipping.
 */

package dev.ngocthanhgl.vikey.ime.text.composing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("sinhala-algorithm")
class AlgorithmicSinhala(
    override val id: String = "sinhala",
    override val label: String = "Sinhala (Phonetic)",
) : Composer {

    override val toRead = 32

    // ──────────────────────────────────────────────────────────────
    //  Vowels: Latin key -> (independent glyph, dependent sign glyph)
    //  Sign glyph is "" for the inherent /a/ (no mark needed).
    //  Ordered longest-Latin-key-first so "aae" is tried before "aa"/"a".
    // ──────────────────────────────────────────────────────────────

    private data class VowelDef(val latin: String, val independent: String, val sign: String)

    private val vowelDefs = listOf(
        VowelDef("au", "ඖ", "ෞ"),
        VowelDef("ai", "ඓ", "ෛ"),
        VowelDef("oo", "ඕ", "ෝ"),
        VowelDef("ee", "ඒ", "ේ"),
        VowelDef("aae", "ඈ", "ෑ"),
        VowelDef("uu", "ඌ", "ූ"),
        VowelDef("ii", "ඊ", "ී"),
        VowelDef("ae", "ඇ", "ැ"),
        VowelDef("aa", "ආ", "ා"),
        VowelDef("i", "ඉ", "ි"),
        VowelDef("u", "උ", "ු"),
        VowelDef("e", "එ", "ෙ"),
        VowelDef("o", "ඔ", "ො"),
        VowelDef("a", "අ", ""),
    ).sortedByDescending { it.latin.length }

    // ──────────────────────────────────────────────────────────────
    //  Consonants: Latin key -> base glyph (inherent /a/ built in).
    //  Case-sensitive: T/D/N/L/Sh are the retroflex/loan series and
    //  are DISTINCT from t/d/n/l/sh — do not lowercase input here.
    // ──────────────────────────────────────────────────────────────

    private val consonantDefs = listOf(
        "ng" to "ඞ", "ny" to "ඤ", "chh" to "ඡ", "ch" to "ච", "kh" to "ඛ",
        "gh" to "ඝ", "jh" to "ඣ", "Th" to "ඨ", "Dh" to "ඪ", "thh" to "ථ",
        "dh" to "ධ", "ph" to "ඵ", "bh" to "භ", "Sh" to "ෂ", "sh" to "ශ",
        "T" to "ට", "D" to "ඩ", "N" to "ණ", "L" to "ළ",
        "th" to "ත", "t" to "ත", "k" to "ක", "g" to "ග", "c" to "ච", "j" to "ජ",
        "d" to "ද", "n" to "න", "p" to "ප", "b" to "බ", "m" to "ම",
        "y" to "ය", "r" to "ර", "l" to "ල", "v" to "ව", "w" to "ව",
        "f" to "ෆ", "s" to "ස", "h" to "හ",
    ).sortedByDescending { it.first.length }

    // ──────────────────────────────────────────────────────────────
    //  Reverse maps: Sinhala glyph -> canonical Latin, used to
    //  reconstruct a Latin buffer from already-committed Sinhala text.
    // ──────────────────────────────────────────────────────────────

    private val reverseConsonant: Map<Char, String> =
        consonantDefs.associate { (latin, glyph) -> glyph[0] to latin }
    private val reverseVowelSign: Map<Char, String> =
        vowelDefs.filter { it.sign.isNotEmpty() }.associate { it.sign[0] to it.latin }
    private val reverseIndependentVowel: Map<Char, String> =
        vowelDefs.associate { it.independent[0] to it.latin }

    private val consonantGlyphs = reverseConsonant.keys
    private val independentVowelGlyphs = reverseIndependentVowel.keys

    // ──────────────────────────────────────────────────────────────
    //  Public API
    // ──────────────────────────────────────────────────────────────

    override fun getActions(precedingText: String, toInsert: String): Pair<Int, String> {
        if (toInsert.length != 1) return 0 to toInsert
        val ch = toInsert[0]

        if (precedingText.isEmpty() || !isSinhalaOrLetter(precedingText.last())) {
            return 0 to transliterate(ch.toString())
        }

        // z = undo: revert the current Sinhala word back to plain Latin.
        if (ch == 'z') {
            val word = lastSinhalaWord(precedingText)
            if (word.isNotEmpty()) {
                val latin = reconstructLatin(word)
                return word.length to latin
            }
        }

        val word = lastSinhalaWord(precedingText)
        val latinSoFar = reconstructLatin(word)
        val newLatin = latinSoFar + ch
        val newSinhala = transliterate(newLatin)

        return word.length to newSinhala
    }

    private fun isSinhalaOrLetter(c: Char): Boolean {
        return c in consonantGlyphs || c in independentVowelGlyphs ||
            c in reverseVowelSign.keys || c == HALANT || c.isLetter()
    }

    private fun lastSinhalaWord(text: String): String {
        return text.takeLastWhile { isSinhalaOrLetter(it) }
    }

    // ──────────────────────────────────────────────────────────────
    //  Forward: Latin buffer -> Sinhala text
    // ──────────────────────────────────────────────────────────────

    private fun transliterate(latin: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < latin.length) {
            val consonant = matchLongest(latin, i, consonantDefs.map { it.first })
            if (consonant != null) {
                val glyph = consonantDefs.first { it.first == consonant }.second
                val afterConsonant = i + consonant.length
                val vowel = matchLongest(latin, afterConsonant, vowelDefs.map { it.latin })
                if (vowel != null) {
                    val def = vowelDefs.first { it.latin == vowel }
                    out.append(glyph).append(def.sign)
                    i = afterConsonant + vowel.length
                } else {
                    // No vowel follows: either end of word or another consonant.
                    // A bare consonant needs an explicit halant.
                    out.append(glyph).append(HALANT)
                    i = afterConsonant
                }
                continue
            }

            val vowel = matchLongest(latin, i, vowelDefs.map { it.latin })
            if (vowel != null) {
                val def = vowelDefs.first { it.latin == vowel }
                out.append(def.independent)
                i += vowel.length
                continue
            }

            // Unrecognized character: pass through unchanged.
            out.append(latin[i])
            i += 1
        }
        return out.toString()
    }

    private fun matchLongest(text: String, from: Int, keys: List<String>): String? {
        if (from >= text.length) return null
        for (key in keys.sortedByDescending { it.length }) {
            if (text.regionMatches(from, key, 0, key.length, ignoreCase = false)) {
                return key
            }
        }
        return null
    }

    // ──────────────────────────────────────────────────────────────
    //  Reverse: committed Sinhala word -> equivalent Latin buffer
    // ──────────────────────────────────────────────────────────────

    private fun reconstructLatin(sinhala: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < sinhala.length) {
            val c = sinhala[i]
            when {
                c in independentVowelGlyphs -> {
                    out.append(reverseIndependentVowel.getValue(c))
                    i += 1
                }
                c in consonantGlyphs -> {
                    val consonantLatin = reverseConsonant.getValue(c)
                    val next = sinhala.getOrNull(i + 1)
                    when {
                        next != null && next in reverseVowelSign.keys -> {
                            out.append(consonantLatin).append(reverseVowelSign.getValue(next))
                            i += 2
                        }
                        next == HALANT -> {
                            out.append(consonantLatin)
                            i += 2
                        }
                        else -> {
                            out.append(consonantLatin).append('a')
                            i += 1
                        }
                    }
                }
                else -> {
                    out.append(c)
                    i += 1
                }
            }
        }
        return out.toString()
    }

    companion object {
        private const val HALANT = '\u0DCA' // ්
    }
}
