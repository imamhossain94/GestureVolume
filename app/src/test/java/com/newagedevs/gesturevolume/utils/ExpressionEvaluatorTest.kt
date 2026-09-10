package com.newagedevs.gesturevolume.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The evaluator sits behind the search bar, where a wrong answer is worse than none: a query
 * that is silently turned into a number the user did not ask for is the failure these guard.
 */
class ExpressionEvaluatorTest {

    @Test
    fun `precedence and parentheses`() {
        assertEquals("14", ExpressionEvaluator.evaluateToText("2+3*4"))
        assertEquals("20", ExpressionEvaluator.evaluateToText("(2+3)*4"))
        assertEquals("2", ExpressionEvaluator.evaluateToText("8/2/2"))
        assertEquals("512", ExpressionEvaluator.evaluateToText("2^3^2"))
    }

    @Test
    fun `unary minus and decimals`() {
        assertEquals("-1.5", ExpressionEvaluator.evaluateToText("-3/2"))
        assertEquals("0.1", ExpressionEvaluator.evaluateToText("0.3-0.2"))
        assertEquals("6", ExpressionEvaluator.evaluateToText("-(-6)"))
    }

    @Test
    fun `friendly spellings`() {
        assertEquals("12", ExpressionEvaluator.evaluateToText("3 × 4"))
        assertEquals("12", ExpressionEvaluator.evaluateToText("3x4"))
        assertEquals("5", ExpressionEvaluator.evaluateToText("10 ÷ 2"))
        assertEquals("4", ExpressionEvaluator.evaluateToText("√16"))
        assertEquals("3", ExpressionEvaluator.evaluateToText("sqrt(9)"))
        assertEquals("1000", ExpressionEvaluator.evaluateToText("1,000"))
    }

    @Test
    fun `modulo`() {
        assertEquals("1", ExpressionEvaluator.evaluateToText("7%3"))
    }

    @Test
    fun `refuses what is not arithmetic`() {
        assertNull(ExpressionEvaluator.evaluate("hello"))
        assertNull(ExpressionEvaluator.evaluate("2+"))
        assertNull(ExpressionEvaluator.evaluate("(2+3"))
        assertNull(ExpressionEvaluator.evaluate("1/0"))
        assertNull(ExpressionEvaluator.evaluate(""))
    }

    @Test
    fun `search bar gate`() {
        assertTrue(ExpressionEvaluator.looksLikeExpression("12*3"))
        assertTrue(ExpressionEvaluator.looksLikeExpression("(1+2)/3"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("42"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("call mom"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("+8801711111111"))
        assertFalse(ExpressionEvaluator.looksLikeExpression("2 apples + 3"))
    }
}
