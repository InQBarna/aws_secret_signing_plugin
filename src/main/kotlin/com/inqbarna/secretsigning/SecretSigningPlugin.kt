package com.inqbarna.secretsigning

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import java.io.File


class SecretSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.pluginManager.withPlugin("com.android.base") {
            val logger = project.logger

            val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

            val extension = project.extensions.create("secretSigning", SecretSigningExtension::class.java)

            project.tasks.register("fetchSecretSigning", FetchSecretsTask::class.java, extension)

            val parsedSigningInfo = parseSecretSigningData(project)
            androidComponents.finalizeDsl { commonExtension ->

                (commonExtension as? ApplicationExtension)?.let { appExtension ->

                    val data = parsedSigningInfo ?: run {
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

            if (parsedSigningInfo == null) {
                logger.lifecycle("Will disable release variants till 'fetchSecretSigning' is properly executed")
                androidComponents.beforeVariants(androidComponents.selector().withBuildType("release")) {
                    logger.lifecycle("Disabling Variant ${it.name}")
                    it.enable = false
                }
            }
        }
    }
}
