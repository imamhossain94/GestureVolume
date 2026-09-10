package com.newagedevs.gesturevolume.utils

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A small arithmetic evaluator for the calculator tile and the search bar.
 *
 * Handles what a pocket calculator handles: `+ - * / ^ %`, parentheses, unary minus, decimal
 * points, and `sqrt(...)`. Deliberately nothing more — no variables, no functions beyond the
 * root — because the search bar has to decide whether a line *is* arithmetic, and the narrower
 * the grammar the fewer ordinary queries get eaten by it.
 *
 * Pure Kotlin with no Android imports, so it is tested on the host JVM like the stream resolver.
 */
object ExpressionEvaluator {

    /** Characters that may appear in an expression, used to reject text before parsing it. */
    private val ALLOWED = Regex("""^[0-9+\-*/^%().,\s×÷√xX]+$""")

    /**
     * Whether [input] looks like arithmetic worth evaluating: at least one operator between
     * numbers, and nothing that is not part of the grammar. A bare number is not an expression.
     */
    fun looksLikeExpression(input: String): Boolean {
        val s = normalize(input)
        if (s.isEmpty()) return false
        if (!ALLOWED.matches(s.replace("sqrt", ""))) return false
        if (!s.any { it.isDigit() }) return false
        // A leading sign is part of the number, not an operator: "+8801711111111" is a phone
        // number, and treating its "+" as arithmetic is what made the search bar answer one.
        return s.count { it in "+-*/^%" } > 0 && !s.matches(Regex("""^[+-]?[0-9.]+$"""))
    }

    /** The value of [input], or null when it does not parse or divides by zero. */
    fun evaluate(input: String): Double? {
        val s = normalize(input)
        if (s.isEmpty()) return null
        return try {
            val parser = Parser(s)
            val value = parser.parseExpression()
            if (!parser.atEnd()) null else value.takeIf { it.isFinite() }
        } catch (_: Exception) {
            null
        }
    }

    /** [evaluate], formatted the way a calculator shows it: no trailing `.0`, up to 10 decimals. */
    fun evaluateToText(input: String): String? {
        val value = evaluate(input) ?: return null
        return format(value)
    }

    fun format(value: Double): String {
        if (value == Math.rint(value) && kotlin.math.abs(value) < 1e15) return value.toLong().toString()
        val text = String.format(java.util.Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        return text
    }

    /** Folds the friendlier spellings — `×`, `÷`, `√`, `x` between digits — onto the grammar. */
    private fun normalize(input: String): String =
        input.trim()
            .replace('×', '*')
            .replace('÷', '/')
            .replace('√', 's') // handled by the parser as sqrt shorthand
            .replace(Regex("""(?<=[0-9)])\s*[xX]\s*(?=[0-9(])"""), "*")
            .replace(",", "")
            .replace("\\s+".toRegex(), "")

    private class Parser(private val s: String) {
        private var i = 0

        fun atEnd(): Boolean = i >= s.length

        private fun peek(): Char? = if (i < s.length) s[i] else null

        fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { i++; value += parseTerm() }
                    '-' -> { i++; value -= parseTerm() }
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                when (peek()) {
                    '*' -> { i++; value *= parseFactor() }
                    '/' -> {
                        i++
                        val divisor = parseFactor()
                        if (divisor == 0.0) throw ArithmeticException("divide by zero")
                        value /= divisor
                    }
                    '%' -> { i++; value %= parseFactor() }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            val base = parseUnary()
            return if (peek() == '^') {
                i++
                base.pow(parseFactor())
            } else {
                base
            }
        }

        private fun parseUnary(): Double = when (peek()) {
            '-' -> { i++; -parseUnary() }
            '+' -> { i++; parseUnary() }
            else -> parsePrimary()
        }

        private fun parsePrimary(): Double {
            val c = peek() ?: throw IllegalStateException("unexpected end")
            if (c == '(') {
                i++
                val value = parseExpression()
                if (peek() != ')') throw IllegalStateException("missing )")
                i++
                return value
            }
            if (s.startsWith("sqrt", i)) {
                i += 4
                return sqrt(parsePrimary())
            }
            if (c == 's') {
                i++
                return sqrt(parseUnary())
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            if (start == i) throw IllegalStateException("number expected")
            return s.substring(start, i).toDouble()
        }
    }
}
