package com.inqbarna.secretsigning

import com.inqbarna.secretsigning.SecretSigningExtensionImpl.Companion.merge
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeTest {

    private val ks = File("a.jks")

    @Test
    fun globalOnlyFallsBackToDefaults() {
        val merged = merge(SigningSpec(keystoreFile = ks), emptyList())
        assertEquals(MergedSigningConfig(ks, "store_pass", "alias_name", "alias_pass"), merged)
        assertTrue(merged.isValid())
    }

    @Test
    fun flavorOverridingOnlyKeystoreKeepsGlobalKeyNames() {
        val global = SigningSpec(File("global.jks"), "g_store", "g_alias", "g_pass")
        val merged = merge(global, listOf(SigningSpec(keystoreFile = ks)))
        assertEquals(MergedSigningConfig(ks, "g_store", "g_alias", "g_pass"), merged)
    }

    @Test
    fun flavorsInDifferentDimensionsCombine() {
        val env = SigningSpec(keystoreFile = ks, keystorePassKey = "env_store")
        val tier = SigningSpec(aliasNameKey = "tier_alias")
        val merged = merge(null, listOf(env, tier))
        assertEquals(MergedSigningConfig(ks, "env_store", "tier_alias", "alias_pass"), merged)
    }

    @Test
    fun higherPriorityDimensionWins() {
        val first = SigningSpec(aliasNameKey = "first")
        val second = SigningSpec(aliasNameKey = "second", aliasPasswordKey = "second_pass")
        val merged = merge(SigningSpec(keystoreFile = ks, aliasNameKey = "global"), listOf(first, second))
        assertEquals("first", merged.aliasNameKey)
        assertEquals("second_pass", merged.aliasPasswordKey)
    }

    @Test
    fun noKeystoreIsInvalid() {
        val merged = merge(SigningSpec(aliasNameKey = "x"), listOf(SigningSpec()))
        assertFalse(merged.isValid())
        assertEquals(listOf("keystoreFile"), merged.reportMissingFields())
    }

    @Test
    fun equalResultsCompareEqual() {
        val a = merge(SigningSpec(keystoreFile = ks), listOf(SigningSpec(aliasNameKey = "x")))
        val b = merge(SigningSpec(keystoreFile = ks, aliasNameKey = "x"), listOf(SigningSpec()))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
