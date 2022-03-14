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
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject


interface SecretSigningExtension {
    var secretName: String?
    var regionName: String?
    var signingConfig: ApkSigningConfig?
    var buildToolsVersion: String?
}

private interface PrivateSigningExtension : VariantExtension {
    val secretName: Property<String>
    val regionName: Property<String>
    val signingConfig: Property<ApkSigningConfig>
    val buildToolsVersion: Property<String>
}

class SecretSigningPlugin : Plugin<Project> {
    override fun apply(project: Project) {

        project.pluginManager.withPlugin("com.android.base") {
            val objectFactory: ObjectFactory = project.objects
            val logger = project.logger

            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            val sdkComponents = androidComponents.sdkComponents


            androidComponents.registerExtension(
                DslExtension.Builder("secretSigning")
                    .extendBuildTypeWith(SecretSigningExtension::class.java)
                    .build()
            ) { config ->

                objectFactory.newInstance(PrivateSigningExtension::class.java).also {
                    val extension = config.buildTypeExtension(SecretSigningExtension::class.java)
                    it.secretName.set(extension.secretName)
                    it.regionName.set(extension.regionName)
                    it.signingConfig.set(extension.signingConfig)
                    it.buildToolsVersion.set(extension.buildToolsVersion)
                }
            }

            androidComponents.finalizeDsl { commonExtension ->
                (commonExtension as? ApplicationExtension)?.let { appExtension ->
                    appExtension.buildTypes.forEach { appBuildType ->
                        val secretSigningExtension =
                            appBuildType.extensions.findByType(SecretSigningExtension::class.java)
                        if (secretSigningExtension != null) {
                            if (secretSigningExtension.signingConfig == null && secretSigningExtension.secretName != null) {
                                throw GradleException("Expected to define secretSigning.signingConfig with proper signing Configuration")
                            }
                            secretSigningExtension.buildToolsVersion = appExtension.buildToolsVersion

                            // ensure we disable native signing config
                            appBuildType.signingConfig = appExtension.signingConfigs.findByName("debug")
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

                val targetApiLevel = variant.targetSdkVersion.apiLevel

                val apkSigningConfig = signingExtension.signingConfig.get()
                val params = SecureSigningParams(
                    signingExtension.secretName.get(),
                    signingExtension.regionName.convention("eu-west-1").get(),
                    requireNotNull(apkSigningConfig.storeFile) { "You need to provide storeFile parameter in ${apkSigningConfig.name}" },
                    signingExtension.buildToolsVersion.get(),
                    SigningConfigVersions.resolveV1Enabled(apkSigningConfig, targetApiLevel),
                    SigningConfigVersions.resolveV2Enabled(apkSigningConfig, targetApiLevel),
                    SigningConfigVersions.resolveV3Enabled(apkSigningConfig, targetApiLevel),
                    SigningConfigVersions.resolveV4Enabled(apkSigningConfig, targetApiLevel),
                )

                val taskProvider = project
                    .tasks
                    .register("secretSign${variant.name.capitalize()}", AWSSecretSigningTask::class.java) {
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
            val outApk = File(outputLocation.asFile, inFile.name)
            parameters.outApk.set(outApk)
            outApk
        }
    }
}

data class SecureSigningParams(
    val secretName: String,
    val regionName: String,
    val storeFile: File,
    val buildToolsVersion: String,
    val enabledV1: Boolean,
    val enabledV2: Boolean,
    val enabledV3: Boolean,
    val enabledV4: Boolean,
) : java.io.Serializable

private fun SecureSigningParams.getApkSignerPath(sdkComponents: SdkComponents): File {
    val sdkDir = sdkComponents.sdkDirectory.get().asFile
    return sdkDir.resolve("build-tools/$buildToolsVersion/apksigner")
}