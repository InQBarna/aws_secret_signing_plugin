package com.inqbarna.secretsigning

import com.android.build.api.artifact.ArtifactTransformationRequest
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.SdkComponents
import com.android.build.api.variant.*
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import java.lang.Thread.sleep
import javax.inject.Inject
import kotlin.reflect.KProperty

interface SecretSigningExtension {
    var secretId: String?
    var signingConfig: ApkSigningConfig?
    var buildToolsVersion: String?
}

private interface PrivateSigningExtension : VariantExtension {
    val secretName: Property<String>
    val signingConfig: Property<ApkSigningConfig>
    val buildToolsVersion: Property<String>
}

private class SigningConfigVersions {
    // copied from agp-sources: https://github.com/guptadeepanshu/agp-sources/blob/acf46ed4e02469b4601d4d921de7eb4a23a9f100/sources/com.android.tools.build/gradle/com/android/build/gradle/internal/signing/SigningConfigVersions.kt
    companion object {
        private const val serialVersionUID = 1L

        // The lowest API with v2 signing support
        const val MIN_V2_SDK = 24
        // The lowest API with v3 signing support
        const val MIN_V3_SDK = 28
        // The lowest API with v4 signing support
        const val MIN_V4_SDK = 30


        // copied from agp-sources: https://github.com/guptadeepanshu/agp-sources/blob/acf46ed4e02469b4601d4d921de7eb4a23a9f100/sources/com.android.tools.build/gradle/com/android/build/api/variant/impl/SigningConfigImpl.kt
        fun resolveV1Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int, minSdk: Int): Boolean {
            val enableV1Signing = dslSigningConfig?.enableV1Signing
            val effectiveMinSdk = targetApi ?: minSdk
            return when {
                // Sign with v1 if it's enabled explicitly.
                enableV1Signing != null -> enableV1Signing
                // We need v1 if minSdk < MIN_V2_SDK.
                effectiveMinSdk < SigningConfigVersions.MIN_V2_SDK -> true
                // We don't need v1 if minSdk >= MIN_V2_SDK and we're signing with v2.
                resolveV2Enabled(dslSigningConfig, targetApi, minSdk) -> false
                // We need v1 if we're not signing with v2 and minSdk < MIN_V3_SDK.
                effectiveMinSdk < SigningConfigVersions.MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v1 only if we're not signing with v3.
                else -> !resolveV3Enabled(dslSigningConfig, targetApi, minSdk)
            }
        }
        fun resolveV2Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int, minSdk: Int): Boolean {
            val enableV2Signing = dslSigningConfig.enableV2Signing
            val effectiveMinSdk = targetApi ?: minSdk
            return when {
                // Don't sign with v2 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < SigningConfigVersions.MIN_V2_SDK -> false
                // Otherwise, sign with v2 if it's enabled explicitly.
                enableV2Signing != null -> enableV2Signing
                // We sign with v2 if minSdk < MIN_V3_SDK, even if we're also signing with v1,
                // because v2 signatures can be verified faster than v1 signatures.
                effectiveMinSdk < SigningConfigVersions.MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v2 only if we're not signing with v3.
                else -> !resolveV3Enabled(dslSigningConfig, targetApi, minSdk)
            }
        }
        fun resolveV3Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int, minSdk: Int): Boolean {
            return when {
                // Don't sign with v3 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < MIN_V3_SDK -> false
                // Otherwise, sign with v3 if it's enabled explicitly.
                else -> dslSigningConfig.enableV3Signing ?: false
            }
        }
        fun resolveV4Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int, minSdk: Int): Boolean {
            return when {
                // Don't sign with v4 if we're installing on a device that doesn't support it.
                targetApi != null && targetApi < MIN_V4_SDK -> false
                // Otherwise, sign with v4 if it's enabled explicitly.
                else -> dslSigningConfig.enableV4Signing ?: false
            }
        }
    }
}

class SecretSigningPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (!project.pluginManager.hasPlugin("com.android.base")) {
            throw GradleException("This plugin is only supported on android projects")
        }

        val objectFactory: ObjectFactory = project.objects
        val logger = project.logger
        val providerFactory = project.providers

        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
        val sdkComponents = androidComponents.sdkComponents


        androidComponents.registerExtension(DslExtension.Builder("secretSigning")
            .extendBuildTypeWith(SecretSigningExtension::class.java)
            .build()) { config ->

            objectFactory.newInstance(PrivateSigningExtension::class.java).also {
                val extension = config.buildTypeExtension(SecretSigningExtension::class.java)
                val secretId = extension.secretId
                it.secretName.set(secretId)
                it.signingConfig.set(extension.signingConfig)
                it.buildToolsVersion.set(extension.buildToolsVersion)
            }
        }

        androidComponents.finalizeDsl { commonExtension ->
            (commonExtension as? ApplicationExtension)?.let { appExtension ->
                appExtension.buildTypes.forEach { appBuildType ->
                    val secretSigningExtension = appBuildType.extensions.findByType(SecretSigningExtension::class.java)
                    if (secretSigningExtension != null) {
                        if (secretSigningExtension.signingConfig == null && secretSigningExtension.secretId != null) {
                            throw GradleException("Expected to define secretSigning.signingConfig with proper signing Configuration")
                        }
                        secretSigningExtension.buildToolsVersion = appExtension.buildToolsVersion

                        // ensure we disable native signing config
                        val debugSigning = appExtension.signingConfigs.findByName("debug")
                        logger.lifecycle("Debug signing: $debugSigning")
                        appBuildType.signingConfig = debugSigning
                    } else {
                        logger.lifecycle("Extension not found on finalize: ${appBuildType.name}")
                    }
                }
            }
        }

        androidComponents.onVariants { variant ->
            if (variant !is ApplicationVariant) {
                logger.info("secretSigning is not valid for variant ${variant.name} as it's not of proper type")
                return@onVariants
            }
            val signingExtension = requireNotNull(variant.getExtension(PrivateSigningExtension::class.java))

            if (!signingExtension.secretName.isPresent || !signingExtension.signingConfig.isPresent) {
                return@onVariants
            }

            logger.lifecycle("onVariant(${variant.name})")

            val targetApiLevel = variant.targetSdkVersion.apiLevel
            val minSdk = variant.minSdkVersion.apiLevel

            val apkSigningConfig = signingExtension.signingConfig.get()
            val params = SecureSigningParams(
                signingExtension.secretName.get(),
                requireNotNull(apkSigningConfig.storeFile) { "You need to provide storeFile parameter in ${apkSigningConfig.name}"},
                signingExtension.buildToolsVersion.get(),
                SigningConfigVersions.resolveV1Enabled(apkSigningConfig, targetApiLevel, minSdk),
                SigningConfigVersions.resolveV2Enabled(apkSigningConfig, targetApiLevel, minSdk),
                SigningConfigVersions.resolveV3Enabled(apkSigningConfig, targetApiLevel, minSdk),
                SigningConfigVersions.resolveV4Enabled(apkSigningConfig, targetApiLevel, minSdk),
            )

            val taskProvider = project
                .tasks
                .register("getSignCredentials${variant.name.capitalize()}", AWSSecretSigningTask::class.java) {
                    it.sdkComponents = sdkComponents
                    it.params = params
                }

            val transformMany = variant
                .artifacts
                .use(taskProvider)
                .wiredWithDirectories(
                    { it.inputApk },
                    { it.outFolder }
                )
                .toTransformMany(SingleArtifact.APK)

            taskProvider.configure {
                it.transformationRequest.set(transformMany)
            }
        }
    }
}

operator fun <T : Any> Property<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
operator fun <T : Any> Property<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    set(value)
}

operator fun <T : Any> Provider<T>.getValue(thisRef: Any?, property: KProperty<*>): T? = get()
operator fun <T : Any> Provider<T>.setValue(thisRef: Any?, property: KProperty<*>, noop: T) {
    throw UnsupportedOperationException("Not supported")
}

data class SecureSigningParams(
    val secretName: String,
    val storeFile: File,
    val buildToolsVersion: String,
    val enabledV1: Boolean,
    val enabledV2: Boolean,
    val enabledV3: Boolean,
    val enabledV4: Boolean,
) : java.io.Serializable

interface SignerParams : WorkParameters, java.io.Serializable {
    val inputApk: RegularFileProperty
    val outApk: RegularFileProperty
    var config: SecureSigningParams
    var apkSignerPath: File
}

abstract class AWSSignWorker @Inject constructor(
    private val params: SignerParams,
    private val execOperations: ExecOperations
) : WorkAction<SignerParams> {

    val config: SecureSigningParams
        get() = params.config

    private val logger by lazy { Logging.getLogger(AWSSignWorker::class.java) }

    @OptIn(ExperimentalStdlibApi::class)
    override fun execute() {

        val storeFile = params.config.storeFile.absolutePath

        val inFile = params.inputApk.asFile.get().absolutePath
        val outFile = params.outApk.asFile.get().absolutePath
        execOperations.exec {
            it.executable = params.apkSignerPath.absolutePath
            it.args = buildList {
                add("sign")
                add("-v")
                if (config.enabledV1) {
                    add("--v1-signing-enabled")
                }
                if (config.enabledV2) {
                    add("--v2-signing-enabled")
                }
                if (config.enabledV3) {
                    add("--v3-signing-enabled")
                }
                if (config.enabledV4) {
                    add("--v4-signing-enabled")
                }
                add("--in")
                add(inFile)
                add("--out")
                add(outFile)
                add("--ks")
                add(storeFile)
                logger.lifecycle("Simulating fetch secret")
                sleep(3000)
                add("--ks-key-alias")
                add("<alias>")
                add("--ks-pass")
                add("pass:<passwordhere>")
                add("--key-pass")
                add("pass:<passwordhere>")
            }
        }.assertNormalExitValue()
        logger.lifecycle("Signed $inFile to $outFile")
    }
}

private fun SecureSigningParams.getApkSignerPath(sdkComponents: SdkComponents): File {
    val sdkDir = sdkComponents.sdkDirectory.get().asFile
    return sdkDir.resolve("build-tools/$buildToolsVersion/apksigner")
}

private abstract class AWSSecretSigningTask @Inject constructor(@Internal val workExecutor: WorkerExecutor): DefaultTask() {

    @get:Internal
    lateinit var params: SecureSigningParams
    @get:Internal
    lateinit var sdkComponents: SdkComponents

    @get:InputFiles
    abstract val inputApk: DirectoryProperty

    @get:OutputDirectory
    abstract val outFolder: DirectoryProperty

    @get:Internal
    abstract val transformationRequest: Property<ArtifactTransformationRequest<AWSSecretSigningTask>>

    @TaskAction
    fun taskAction() {
        transformationRequest.get().submit(
            this,
            workExecutor.noIsolation(),
            AWSSignWorker::class.java
        ) {
            builtArtifact: BuiltArtifact, outputLocation: Directory, parameters: SignerParams ->
            parameters.apkSignerPath = params.getApkSignerPath(sdkComponents)
            parameters.config = params
            val inFile = File(builtArtifact.outputFile)
            parameters.inputApk.set(inFile)
//            val outApk = File(outputLocation.asFile, inFile.name.replace("-unsigned", ""))
            val outApk = File(outputLocation.asFile, inFile.name)
            parameters.outApk.set(outApk)
            outApk
        }
    }
}
