package com.inqbarna.secretsigning.functional

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecretSigningFunctionalTest {

    @TempDir
    lateinit var dir: File

    private val fixture by lazy { Fixture(dir) }

    private fun flavors(vararg names: Pair<String, String>, blocks: Map<String, String> = emptyMap()) =
        names.joinToString("\n") { (name, dimension) ->
            """productFlavors.create("$name") { dimension = "$dimension"
                ${blocks[name].orEmpty()}
            }"""
        }

    @Test
    fun noFlavorsGlobalConfig() {
        val ks = fixture.keystore("global", "global-pass", "global-alias")
        fixture.secret("store_pass", "global-pass").secret("alias_name", "global-alias").secret("alias_pass", "global-pass")
        fixture.app(fixture.signingBlock(ks))

        assertEquals(mapOf("release" to ("global.jks" to "global-alias")), fixture.releaseSigning())
    }

    @Test
    fun noKeystoreDisablesReleaseVariants() {
        fixture.secret("store_pass", "x")
        fixture.app(
            """
            flavorDimensions += "env"
            ${flavors("demo" to "env", "prod" to "env")}
            """
        )

        val result = fixture.run(":app:signingReport")
        assertTrue("no valid config for 'demoRelease', missing fields: keystoreFile" in result.output, result.output)
        assertTrue("no valid config for 'prodRelease', missing fields: keystoreFile" in result.output, result.output)
        assertEquals(emptyMap(), Fixture.parseSigningReport(result.output))
        assertEquals(emptySet(), fixture.assembleReleaseTasks())
    }

    @Test
    fun singleDimensionPerFlavorKeystores() {
        val demo = fixture.keystore("demo", "demo-pass", "demo-alias")
        val prod = fixture.keystore("prod", "prod-pass", "prod-alias")
        fixture.secret("demo_store", "demo-pass").secret("demo_alias", "demo-alias").secret("demo_key", "demo-pass")
            .secret("prod_store", "prod-pass").secret("prod_alias", "prod-alias").secret("prod_key", "prod-pass")
        fixture.app(
            """
            flavorDimensions += "env"
            ${
                flavors(
                    "demo" to "env", "prod" to "env",
                    blocks = mapOf(
                        "demo" to fixture.signingBlock(demo, "demo_store", "demo_alias", "demo_key"),
                        "prod" to fixture.signingBlock(prod, "prod_store", "prod_alias", "prod_key")
                    )
                )
            }
            """
        )

        assertEquals(
            mapOf(
                "demoRelease" to ("demo.jks" to "demo-alias"),
                "prodRelease" to ("prod.jks" to "prod-alias")
            ),
            fixture.releaseSigning()
        )
        assertEquals(setOf("assembleDemoRelease", "assembleProdRelease"), fixture.assembleReleaseTasks())
    }

    @Test
    fun flavorOverridingOnlyKeystoreInheritsGlobalKeyNames() {
        val ks = fixture.keystore("demo", "shared-pass", "shared-alias")
        fixture.secret("my_store", "shared-pass").secret("my_alias", "shared-alias").secret("my_key", "shared-pass")
        fixture.app(
            """
            flavorDimensions += "env"
            ${flavors("demo" to "env", blocks = mapOf("demo" to fixture.signingBlock(ks)))}
            ${fixture.signingBlock(storePassKey = "my_store", aliasNameKey = "my_alias", aliasPassKey = "my_key")}
            """
        )

        assertEquals(mapOf("demoRelease" to ("demo.jks" to "shared-alias")), fixture.releaseSigning())
    }

    @Test
    fun twoDimensionsMergeFieldsAndDisableIncompleteCombination() {
        val demo = fixture.keystore("demo", "demo-pass", "demo-alias", "paid-alias")
        val prod = fixture.keystore("prod", "prod-pass", "demo-alias", "paid-alias")
        fixture.secret("store_pass", "demo-pass").secret("alias_name", "demo-alias").secret("alias_pass", "demo-pass")
            .secret("prod_store", "prod-pass").secret("prod_alias", "paid-alias").secret("prod_key", "prod-pass")
        // env sets keystore (+ pass keys for prod), tier "paid" sets the alias name key only. "stage" has no keystore.
        fixture.app(
            """
            flavorDimensions += listOf("env", "tier")
            ${
                flavors(
                    "demo" to "env", "prod" to "env", "stage" to "env", "free" to "tier", "paid" to "tier",
                    blocks = mapOf(
                        "demo" to fixture.signingBlock(demo),
                        "prod" to fixture.signingBlock(prod, "prod_store", null, "prod_key"),
                        "paid" to fixture.signingBlock(aliasNameKey = "prod_alias")
                    )
                )
            }
            """
        )

        val report = fixture.releaseSigning()
        assertEquals(
            mapOf(
                "demoFreeRelease" to ("demo.jks" to "demo-alias"),
                "demoPaidRelease" to ("demo.jks" to "paid-alias"),
                "prodFreeRelease" to ("prod.jks" to "demo-alias"),
                "prodPaidRelease" to ("prod.jks" to "paid-alias")
            ),
            report
        )
        assertEquals(
            setOf("assembleDemoFreeRelease", "assembleDemoPaidRelease", "assembleProdFreeRelease", "assembleProdPaidRelease"),
            fixture.assembleReleaseTasks()
        )
    }

    @Test
    fun shorthandSyntaxWithImportedFlavorHelper() {
        val demo = fixture.keystore("demo", "shared-pass", "shared-alias")
        val prod = fixture.keystore("prod", "shared-pass", "shared-alias")
        fixture.secret("my_store", "shared-pass").secret("my_alias", "shared-alias").secret("my_key", "shared-pass")
        // Global block through the generated accessor, flavor blocks through the imported helper. Without the import,
        // the flavor blocks would resolve to the global accessor and both variants would end up with prod.jks.
        fixture.app(
            """
            flavorDimensions += "env"
            ${
                flavors(
                    "demo" to "env", "prod" to "env", "stage" to "env",
                    blocks = mapOf(
                        "demo" to fixture.signingBlock(demo, shorthand = true),
                        "prod" to fixture.signingBlock(prod, shorthand = true)
                    )
                )
            }
            ${fixture.signingBlock(storePassKey = "my_store", aliasNameKey = "my_alias", aliasPassKey = "my_key", shorthand = true)}
            """,
            imports = "import com.inqbarna.secretsigning.secretSigning"
        )

        assertEquals(
            mapOf(
                "demoRelease" to ("demo.jks" to "shared-alias"),
                "prodRelease" to ("prod.jks" to "shared-alias")
            ),
            fixture.releaseSigning()
        )
        assertEquals(setOf("assembleDemoRelease", "assembleProdRelease"), fixture.assembleReleaseTasks())
    }

    @Test
    fun missingSecretKeyFailsBuild() {
        val ks = fixture.keystore("global", "global-pass", "global-alias")
        fixture.secret("store_pass", "global-pass")
        fixture.app(fixture.signingBlock(ks))

        val result = fixture.runAndFail(":app:signingReport")
        assertTrue("Secret 'alias_name' not found" in result.output, result.output)
    }

    @Test
    fun refreshSecretsRewritesCache() {
        val ks = fixture.keystore("global", "global-pass", "global-alias")
        fixture.secret("store_pass", "global-pass").secret("alias_name", "global-alias").secret("alias_pass", "global-pass")
        fixture.app(fixture.signingBlock(ks))
        fixture.run(":app:help")

        val cache = File(dir, "app/build/secrets/secrets.json")
        assertTrue("brand_new" !in cache.readText())
        fixture.secret("brand_new", "value")
        fixture.run(":app:refreshSecrets")
        assertTrue("brand_new" in cache.readText(), cache.readText())
    }

    @Test
    @Tag("slow")
    fun assembleReleaseProducesApk() {
        val ks = fixture.keystore("demo", "demo-pass", "demo-alias")
        fixture.secret("store_pass", "demo-pass").secret("alias_name", "demo-alias").secret("alias_pass", "demo-pass")
        fixture.app(
            """
            flavorDimensions += "env"
            ${flavors("demo" to "env", blocks = mapOf("demo" to fixture.signingBlock(ks)))}
            """
        )

        fixture.run(":app:assembleDemoRelease")
        val apks = File(dir, "app/build/outputs/apk/demo/release").listFiles { f -> f.extension == "apk" }.orEmpty()
        assertTrue(apks.isNotEmpty(), "no apk produced")
    }
}
