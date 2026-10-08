package com.inqbarna.secrets

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SecretsExtensionTest {

    private class FakeSecretFetcher(var data: Map<String, String>) : SecretFetcher {
        var calls = 0
        override fun fetch(secretName: String, regionName: String): Map<String, String> {
            calls++
            return data
        }
    }

    @TempDir
    lateinit var dir: File

    private lateinit var project: Project
    private lateinit var ext: SecretExtensionImpl
    private lateinit var cache: File

    @BeforeTest
    fun setUp() {
        project = ProjectBuilder.builder().withProjectDir(dir).build()
        project.pluginManager.apply(SecretsPlugin::class.java)
        ext = project.extensions.getByType(SecretsExtension::class.java) as SecretExtensionImpl
        ext.secretName.set("test-secret")
        cache = File(dir, "build/secrets/secrets.json")
    }

    private fun fetcher(data: Map<String, String>) = FakeSecretFetcher(data).also { ext.fetcher = it }

    @Test
    fun missingCacheFetchesOnce() {
        val f = fetcher(mapOf("a" to "1"))
        assertEquals("1", ext["a"].get())
        assertEquals(1, f.calls)
    }

    @Test
    fun cachedKeyDoesNotFetch() {
        val f = fetcher(mapOf("a" to "1", "b" to "2"))
        ext["a"].get()
        assertEquals("2", ext["b"].get())
        assertEquals(1, f.calls)
    }

    @Test
    fun existingCacheIsUsedWithoutFetching() {
        cache.parentFile.mkdirs()
        cache.writeText("""{"a": "cached"}""")
        val f = fetcher(emptyMap())
        assertEquals("cached", ext["a"].get())
        assertEquals(0, f.calls)
    }

    @Test
    fun keyMissingFromStaleCacheRefetchesOnce() {
        cache.parentFile.mkdirs()
        cache.writeText("""{"a": "old"}""")
        val f = fetcher(mapOf("a" to "old", "b" to "new"))
        assertEquals("new", ext["b"].get())
        assertEquals(1, f.calls)
    }

    @Test
    fun keyStillMissingAfterRefetchFails() {
        cache.parentFile.mkdirs()
        cache.writeText("""{"a": "old"}""")
        val f = fetcher(mapOf("a" to "old"))
        assertFailsWith<GradleException> { ext["zzz"].get() }
        assertEquals(1, f.calls)
    }

    @Test
    fun freshlyFetchedFileWithoutKeyFailsWithoutSecondFetch() {
        val f = fetcher(mapOf("a" to "1"))
        assertFailsWith<GradleException> { ext["zzz"].get() }
        assertEquals(1, f.calls)
    }

    @Test
    fun providersAreMemoized() {
        fetcher(mapOf("a" to "1"))
        assertSame(ext["a"], ext["a"])
    }
}
