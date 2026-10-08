package com.inqbarna.secrets

import org.gradle.api.GradleException
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocalFileSecretFetcherTest {

    @TempDir
    lateinit var dir: File

    @Test
    fun readsValidFile() {
        val file = File(dir, "s.json").apply { writeText("""{"a": "1", "b": "2"}""") }
        assertEquals(mapOf("a" to "1", "b" to "2"), LocalFileSecretFetcher(file).fetch("ignored", "ignored"))
    }

    @Test
    fun missingFileFails() {
        assertFailsWith<GradleException> { LocalFileSecretFetcher(File(dir, "nope.json")).fetch("n", "r") }
    }

    @Test
    fun malformedJsonFails() {
        val file = File(dir, "bad.json").apply { writeText("{ not json") }
        assertFailsWith<GradleException> { LocalFileSecretFetcher(file).fetch("n", "r") }
    }

    @Test
    fun nonStringValuesFail() {
        val file = File(dir, "nested.json").apply { writeText("""{"a": {"b": 1}}""") }
        assertFailsWith<GradleException> { LocalFileSecretFetcher(file).fetch("n", "r") }
    }
}
