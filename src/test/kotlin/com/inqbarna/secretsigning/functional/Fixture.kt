package com.inqbarna.secretsigning.functional

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/** A throwaway Android application project that applies the plugin under test and reads secrets from a local file. */
class Fixture(private val dir: File) {

    private val secrets = linkedMapOf<String, String>()

    init {
        val sdk = findAndroidSdk()
        assumeTrue(sdk != null, "Android SDK not found (ANDROID_HOME, ANDROID_SDK_ROOT or ~/Library/Android/sdk)")
        write("settings.gradle.kts", """
            pluginManagement {
                repositories { google(); mavenCentral(); gradlePluginPortal() }
            }
            dependencyResolutionManagement {
                repositories { google(); mavenCentral() }
            }
            rootProject.name = "fixture"
            include(":app")
        """.trimIndent())
        write("build.gradle.kts", "")
        write("local.properties", "sdk.dir=${sdk!!.absolutePath.replace("\\", "\\\\")}")
        write("gradle.properties", "org.gradle.jvmargs=-Xmx1g\nandroid.useAndroidX=true")
        write("app/src/main/AndroidManifest.xml", "<manifest />")
    }

    /** Generates a PKCS12 keystore in the fixture with one key per alias, all sharing [storePass]. */
    fun keystore(name: String, storePass: String, vararg aliases: String): File {
        val file = File(dir, "keystores/$name.jks")
        file.parentFile.mkdirs()
        val keytool = File(System.getProperty("java.home"), "bin/keytool").absolutePath
        for (alias in aliases) {
            val process = ProcessBuilder(
                keytool, "-genkeypair", "-keystore", file.absolutePath, "-storetype", "PKCS12",
                "-storepass", storePass, "-keypass", storePass, "-alias", alias,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "30", "-dname", "CN=test"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "keytool failed: $output" }
        }
        return file
    }

    fun secret(key: String, value: String) = apply { secrets[key] = value }

    /** Writes app/build.gradle.kts with the standard header, [body] goes inside the `android { }` block. */
    fun app(androidBody: String, extra: String = "", imports: String = "") {
        write("app/build.gradle.kts", """
            import com.inqbarna.secretsigning.SecretSigningExtension
            $imports

            plugins {
                id("com.android.application")
                id("com.inqbarna.secretsigning")
            }
            android {
                namespace = "com.example.fixture"
                compileSdk = 35
                defaultConfig { minSdk = 24 }
                buildTypes { release { isMinifyEnabled = false } }
                $androidBody
            }
            $extra
        """.trimIndent())
    }

    private fun writeSecrets(): File = write("secrets.json", secrets.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" })

    fun run(vararg args: String): BuildResult = runner(*args).build()

    fun runAndFail(vararg args: String): BuildResult = runner(*args).buildAndFail()

    private fun runner(vararg args: String): GradleRunner {
        val secretsFile = writeSecrets()
        return GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(*args, "-Pinqbarna.secrets.localFile=${secretsFile.absolutePath}", "--stacktrace")
    }

    /** Release variants as reported by signingReport: variant name to (store file name, alias). */
    fun releaseSigning(): Map<String, Pair<String, String>> =
        parseSigningReport(run(":app:signingReport").output)

    /** Names of the assemble<Flavors>Release variant tasks that exist (the aggregate assembleRelease is not included). */
    fun assembleReleaseTasks(): Set<String> =
        run(":app:tasks", "--all").output.lineSequence()
            .map { it.substringBefore(' ') }
            .filter { Regex("assemble[A-Z]\\w*Release").matches(it) }
            .toSet()

    /**
     * A `secretSigning { }` block, usable inside `android { }` or a product flavor. By default it uses the explicit
     * `extensions.configure` form; with [shorthand] it uses `secretSigning { }` (the generated accessor on `android { }`,
     * or the imported flavor helper).
     */
    fun signingBlock(
        keystore: File? = null,
        storePassKey: String? = null,
        aliasNameKey: String? = null,
        aliasPassKey: String? = null,
        shorthand: Boolean = false
    ): String =
        buildString {
            appendLine(if (shorthand) "secretSigning {" else "extensions.configure<SecretSigningExtension>(\"secretSigning\") {")
            keystore?.let { appendLine("    keystoreFile = file(\"${it.absolutePath}\")") }
            storePassKey?.let { appendLine("    keystorePassKey.set(\"$it\")") }
            aliasNameKey?.let { appendLine("    aliasNameKey.set(\"$it\")") }
            aliasPassKey?.let { appendLine("    aliasPasswordKey.set(\"$it\")") }
            appendLine("}")
        }

    private fun write(path: String, content: String): File =
        File(dir, path).also { it.parentFile.mkdirs(); it.writeText(content) }

    companion object {
        fun findAndroidSdk(): File? {
            val candidates = listOfNotNull(
                System.getenv("ANDROID_HOME"),
                System.getenv("ANDROID_SDK_ROOT"),
                File(System.getProperty("user.home"), "Library/Android/sdk").path
            )
            return candidates.map(::File).firstOrNull { it.isDirectory }
        }

        /** Parses `Variant:` / `Store:` / `Alias:` blocks of AGP's signingReport. Only release variants are kept. */
        fun parseSigningReport(output: String): Map<String, Pair<String, String>> {
            val result = linkedMapOf<String, Pair<String, String>>()
            var variant: String? = null
            var store: String? = null
            for (line in output.lineSequence().map { it.trim() }) {
                when {
                    line.startsWith("Variant: ") -> {
                        variant = line.removePrefix("Variant: ").takeIf { it == "release" || it.endsWith("Release") }
                        store = null
                    }
                    line.startsWith("Store: ") -> store = File(line.removePrefix("Store: ")).name
                    line.startsWith("Alias: ") -> {
                        val v = variant
                        if (v != null && store != null) result.putIfAbsent(v, store!! to line.removePrefix("Alias: "))
                    }
                }
            }
            return result
        }
    }
}
