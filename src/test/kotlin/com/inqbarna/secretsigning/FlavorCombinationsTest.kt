package com.inqbarna.secretsigning

import kotlin.test.Test
import kotlin.test.assertEquals

class FlavorCombinationsTest {

    @Test
    fun noDimensionsGivesSingleEmptyCombination() {
        assertEquals(listOf(emptyList()), flavorCombinations(emptyList(), emptyMap<String, List<String>>()))
    }

    @Test
    fun singleDimension() {
        val result = flavorCombinations(listOf("env"), mapOf("env" to listOf("demo", "prod")))
        assertEquals(listOf(listOf("demo"), listOf("prod")), result)
    }

    @Test
    fun cartesianProductOrderedByDimension() {
        val result = flavorCombinations(
            listOf("env", "tier"),
            mapOf("tier" to listOf("free", "paid"), "env" to listOf("demo", "prod"))
        )
        assertEquals(
            listOf(
                listOf("demo", "free"), listOf("demo", "paid"),
                listOf("prod", "free"), listOf("prod", "paid")
            ),
            result
        )
    }

    @Test
    fun dimensionWithoutFlavorsYieldsNoCombinations() {
        val result = flavorCombinations(listOf("env", "tier"), mapOf("env" to listOf("demo")))
        assertEquals(emptyList(), result)
    }
}
