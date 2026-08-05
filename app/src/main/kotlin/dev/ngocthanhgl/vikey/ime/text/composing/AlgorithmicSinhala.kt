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
 *
 * NOTE: VowelDef and all lookup tables live in the top-level / companion
 * scope (NOT nested inside the @Serializable class body). Nesting a private
 * data class + sortedByDescending{} lambda inside an @Serializable class's
 * instance property initializers triggers a Kotlin serialization-plugin
 * compiler crash ("Parent of element ... is not initialized"). Keeping them
 * outside avoids that entirely and also means the tables are built once,
 * not per-instance.
 */

package dev.ngocthanhgl.vikey.ime.text.composing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ──────────────────────────────────────────────────────────────
//  Vowels: Latin key -> (independent glyph, dependent sign glyph)
//  Sign glyph is "" for the inherent /a/ (no mark needed).
// ──────────────────────────────────────────────────────────────

private data class SinhalaVowelDef(val latin: String, val independent: String, val sign: String)

private val SINHALA_VOWEL_DEFS: List<SinhalaVowelDef> = listOf(
    SinhalaVowelDef("au", "ඖ", "ෞ"),
    SinhalaVowelDef("ai", "ඓ", "ෛ"),
    SinhalaVowelDef("oo", "ඕ", "ෝ"),
    SinhalaVowelDef("ee", "ඒ", "ේ"),
    SinhalaVowelDef("aae", "ඈ", "ෑ"),
    SinhalaVowelDef("uu", "ඌ", "ූ"),
    SinhalaVowelDef("ii", "ඊ", "ී"),
    SinhalaVowelDef("ae", "ඇ", "ැ"),
    SinhalaVowelDef("aa", "ආ", "ා"),
    SinhalaVowelDef("i", "ඉ", "ි"),
    SinhalaVowelDef("u", "උ", "ු"),
    SinhalaVowelDef("e", "එ", "ෙ"),
    SinhalaVowelDef("o", "ඔ", "ො"),
    SinhalaVowelDef("a", "අ", ""),
).sortedByDescending { def -> def.latin.length }

// ──────────────────────────────────────────────────────────────
//  Consonants: Latin key -> base glyph (inherent /a/ built in).
//  Case-sensitive: T/D/N/L/Sh are the retroflex/loan series and
//  are DISTINCT from t/d/n/l/sh — do not lowercase input here.
// ──────────────────────────────────────────────────────────────

private val SINHALA_CONSONANT_DEFS: List<Pair<String, String>> = listOf(
    "ng" to "ඞ", "ny" to "ඤ", "chh" to "ඡ", "ch" to "ච", "kh" to "ඛ",
    "gh" to "ඝ", "jh" to "ඣ", "Th" to "ඨ", "Dh" to "ඪ", "thh" to "ථ",
    "dh" to "ධ", "ph" to "ඵ", "bh" to "භ", "Sh" to "ෂ", "sh" to "ශ",
    "T" to "ට", "D" to "ඩ", "N" to "ණ", "L" to "ළ",
    "th" to "ත", "t" to "ත", "k" to "ක", "g" to "ග", "c" to "ච", "j" to "ජ",
    "d" to "ද", "n" to "න", "p" to "ප", "b" to "බ", "m" to "ම",
    "y" to "ය", "r" to "ර", "l" to "ල", "v" to "ව", "w" to "ව",
    "f" to "ෆ", "s" to "ස", "h" to "හ",
).sortedByDescending { pair -> pair.first.length }

private const val SINHALA_HALANT = '\u0DCA' // ්

// ──────────────────────────────────────────────────────────────
//  Reverse maps: Sinhala glyph -> canonical Latin, used to
//  reconstruct a Latin buffer from already-committed Sinhala text.
// ──────────────────────────────────────────────────────────────

private val SINHALA_REVERSE_CONSONANT: Map<Char, String> =
    SINHALA_CONSONANT_DEFS.associate { pair -> pair.second[0] to pair.first }
private val SINHALA_REVERSE_VOWEL_SIGN: Map<Char, String> =
    SINHALA_VOWEL_DEFS.filter { def -> def.sign.isNotEmpty() }.associate { def -> def.sign[0] to def.latin }
private val SINHALA_REVERSE_INDEPENDENT_VOWEL: Map<Char, String> =
    SINHALA_VOWEL_DEFS.associate { def -> def.independent[0] to def.latin }

private val SINHALA_CONSONANT_GLYPHS: Set<Char> = SINHALA_REVERSE_CONSONANT.keys
private val SINHALA_INDEPENDENT_VOWEL_GLYPHS: Set<Char> = SINHALA_REVERSE_INDEPENDENT_VOWEL.keys
private val SINHALA_CONSONANT_LATIN_KEYS: List<String> = SINHALA_CONSONANT_DEFS.map { pair -> pair.first }
private val SINHALA_VOWEL_LATIN_KEYS: List<String> = SINHALA_VOWEL_DEFS.map { def -> def.latin }

@Serializable
@SerialName("sinhala-algorithm")
class AlgorithmicSinhala(
    override val id: String = "sinhala",
    override val label: String = "Sinhala (Phonetic)",
) : Composer {

    override val toRead = 32

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
        return c in SINHALA_CONSONANT_GLYPHS || c in SINHALA_INDEPENDENT_VOWEL_GLYPHS ||
            c in SINHALA_REVERSE_VOWEL_SIGN.keys || c == SINHALA_HALANT || c.isLetter()
    }

    private fun lastSinhalaWord(text: String): String {
        return text.takeLastWhile { c -> isSinhalaOrLetter(c) }
    }

    // ──────────────────────────────────────────────────────────────
    //  Forward: Latin buffer -> Sinhala text
    // ──────────────────────────────────────────────────────────────

    private fun transliterate(latin: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < latin.length) {
            val consonant = matchLongest(latin, i, SINHALA_CONSONANT_LATIN_KEYS)
            if (consonant != null) {
                val glyph = SINHALA_CONSONANT_DEFS.first { pair -> pair.first == consonant }.second
                val afterConsonant = i + consonant.length
                val vowel = matchLongest(latin, afterConsonant, SINHALA_VOWEL_LATIN_KEYS)
                if (vowel != null) {
                    val def = SINHALA_VOWEL_DEFS.first { d -> d.latin == vowel }
                    out.append(glyph).append(def.sign)
                    i = afterConsonant + vowel.length
                } else {
                    // No vowel follows: either end of word or another consonant.
                    // A bare consonant needs an explicit halant.
                    out.append(glyph).append(SINHALA_HALANT)
                    i = afterConsonant
                }
                continue
            }

            val vowel = matchLongest(latin, i, SINHALA_VOWEL_LATIN_KEYS)
            if (vowel != null) {
                val def = SINHALA_VOWEL_DEFS.first { d -> d.latin == vowel }
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
        for (key in keys) {
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
                c in SINHALA_INDEPENDENT_VOWEL_GLYPHS -> {
                    out.append(SINHALA_REVERSE_INDEPENDENT_VOWEL.getValue(c))
                    i += 1
                }
                c in SINHALA_CONSONANT_GLYPHS -> {
                    val consonantLatin = SINHALA_REVERSE_CONSONANT.getValue(c)
                    val next = sinhala.getOrNull(i + 1)
                    when {
                        next != null && next in SINHALA_REVERSE_VOWEL_SIGN.keys -> {
                            out.append(consonantLatin).append(SINHALA_REVERSE_VOWEL_SIGN.getValue(next))
                            i += 2
                        }
                        next == SINHALA_HALANT -> {
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
}
