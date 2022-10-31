package com.inqbarna.secretsigning

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.LoggingManager
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KProperty1


interface SecretSigningExtension {
    var secretName: String?
    var regionName: String?
    var keystoreFile: File?
    var targetBuildType: String?
}

class SecretSigningPlugin : Plugin<Project> {

    private fun parseSecretSigningData(project: Project): SignConfigSecret? {
        val secretSignFile = FetchSecretsTask.getSigningDataFile(project)
        return if (secretSignFile.exists()) {
            val gson = GsonBuilder().create()
            try {
                gson.fromJson(secretSignFile.reader(), SignConfigSecret::class.java)
            } catch (e: Exception) {
                project.logger.error("Failed to decode 'secretSigning' data file!!", e)
                null
            }
        } else {
            null
        }
    }
    override fun apply(project: Project) {

        project.pluginManager.withPlugin("com.android.base") {
            val objectFactory: ObjectFactory = project.objects
            val logger = project.logger

            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)
            val sdkComponents = androidComponents.sdkComponents

            val extension = project.extensions.create("secretSigning", SecretSigningExtension::class.java)

            project.tasks.register("fetchSecretSigning", FetchSecretsTask::class.java, extension)

            androidComponents.finalizeDsl { commonExtension ->
                (commonExtension as? ApplicationExtension)?.let { appExtension ->

                    val data = parseSecretSigningData(project) ?: run {
                        logger.log(LogLevel.WARN,
                            """There's no secret signing information. Please configure 'secretSigning' extension
                                | and execute 'fetchSecretSigning' task then sync project again to get signature configured
                            """.trimMargin()
                        )
                        return@let
                    }

                    val signingConfig = appExtension.signingConfigs.create(data.signingName) {
                        it.storeFile = File(data.keystoreFile)
                        it.storePassword = data.signingInfo.store_pass
                        it.keyAlias = data.signingInfo.alias_name
                        it.keyPassword = data.signingInfo.alias_pass
                    }

                    appExtension.buildTypes.forEach { appBuildType ->
                        if (appBuildType.name == data.targetBuildType) {
                            logger.lifecycle("Configured '${appBuildType.name}' signingConfig with secret information!!")
                            appBuildType.signingConfig = signingConfig
                        }
                    }
                }
            }
        }
    }
}

private abstract class FetchSecretsTask @Inject constructor(
    @Internal val extension: SecretSigningExtension
    ) : DefaultTask() {

    init {
        description = "Fetch secret signing information from AWS"
    }

    @TaskAction
    fun fetchSecrets() {
        val secretName = requireExtensionProperty(extension, SecretSigningExtension::secretName)
        val regionName = requireExtensionProperty(extension, SecretSigningExtension::regionName)
        val file = requireExtensionProperty(extension, SecretSigningExtension::keystoreFile)
        val targetBuildType = extension.targetBuildType ?: "release"

        if (!file.exists()) {
            throw GradleException("Illegal secret signing configuration. Keystore file '$file' doesn't exist!")
        }

        val secretInfo = getSecret(secretName, regionName)
        val gson = GsonBuilder().create()
        getSigningDataFile(project).outputStream().writer().use {
            gson.toJson(
                SignConfigSecret(
                    targetBuildType,
                    file.absolutePath,
                    targetBuildType,
                    secretInfo
                ),
                SignConfigSecret::class.java,
                it
            )
        }
    }

    companion object {
        fun getSigningDataFile(project: Project): File {
            val directory = project.layout.buildDirectory.get().dir("secret_signing")
            val dirFile = directory.asFile
            if (!dirFile.exists()) {
                dirFile.mkdirs()
            }
            return File(dirFile, "signing.dat")
        }
    }
}

data class SignConfigSecret(
    val targetBuildType: String,
    val keystoreFile: String,
    val signingName: String,
    val signingInfo: SecretInfo
)

private fun <V : Any> requireExtensionProperty(
    extension: SecretSigningExtension,
    property: KProperty1<SecretSigningExtension, V?>
): V {
    return property.get(extension)
        ?: throw GradleException("Secret signing configuration has not been properly configured, missing: 'secretSigning.${property.name}'")
}

private inline fun LoggingManager.upgradeLoggingToLevel(level: LogLevel, block: () -> Unit) {
    val originalStdoutLevel = standardOutputCaptureLevel
    val originalStderrLevel = standardErrorCaptureLevel
    try {
        captureStandardError(level)
        captureStandardOutput(level)
        block()
    } finally {
        captureStandardError(originalStderrLevel)
        captureStandardOutput(originalStdoutLevel)
    }
}
