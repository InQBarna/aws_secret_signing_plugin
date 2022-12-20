/*
 * Copyright 2022 Inqbarna Kenkyuu Jo S.L
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.inqbarna.secretsigning

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.VariantSelector
import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.configurationcache.extensions.capitalized
import java.io.File
import java.util.regex.Pattern

@Serializable
data class SecretInfo(
    val alias_name: String,
    val alias_pass: String,
    val store_pass: String
)

@Serializable
data class SignConfigSecret(
    val targetBuildType: String,
    val targetFlavorType: String,
    val keystoreFile: String,
    val signingName: String,
    val signingInfo: SecretInfo
) {
    fun firstDimensionValue(): String {
        return targetFlavorType.splitWords().first()
    }
}

data class SigningConfigCollection(
    private val items: List<SignConfigSecret>,
    private val project: Project
) {
    fun isEmpty(): Boolean = items.isEmpty()

    fun configureSinging(androidComponentsExtension: ApplicationAndroidComponentsExtension) {
        if (items.isEmpty()) {
            project.logger.lifecycle(
                """There's no secret signing information. Please configure 'secretSigning' extension
                   | and execute 'fetchSecretSigning' task then sync project again to get signature configured
                """.trimMargin()
            )
            androidComponentsExtension.beforeVariants(androidComponentsExtension.selector().withBuildType("release")) {
                project.logger.lifecycle("Disabling ${it.name} because there is no valid configuration")
            }
            return
        }

        androidComponentsExtension.finalizeDsl { ext ->
            val signingConfigs = items.associate {
                it.firstDimensionValue() to ext.signingConfigs.create(it.signingName) { signConfig ->
                    signConfig.keyAlias = it.signingInfo.alias_name
                    signConfig.keyPassword = it.signingInfo.alias_pass
                    signConfig.storeFile = File(it.keystoreFile)
                    signConfig.storePassword = it.signingInfo.store_pass
                }
            }

            if (ext.productFlavors.isNotEmpty()) {
                ext.productFlavors.forEach { flavor ->
                    val apkSigningConfig = signingConfigs[flavor.name]
                    apkSigningConfig?.let {
                        project.logger.lifecycle("Configuring flavor ${flavor.name} with secret signing info from AWS")
                        flavor.signingConfig = it
                    }
                }
            } else {
                val entry = signingConfigs.entries.single()
                ext.buildTypes.forEach { buildType ->
                    if (buildType.name == entry.key) {
                        project.logger.lifecycle("Configured ${buildType.name} with secret information from AWS")
                        buildType.signingConfig = entry.value
                    }
                }
            }
        }

        val configuredVariants = items.filterNot { it.targetFlavorType == it.targetBuildType }.map { it.targetFlavorType }
        val defaultConfig = items.firstOrNull { it.targetFlavorType == it.targetBuildType }
        androidComponentsExtension.beforeVariants(androidComponentsExtension.selector().withBuildType("release")) {
            if (it.flavorName.isNullOrEmpty()) {
                if (defaultConfig == null) {
                    project.logger.lifecycle("Disabling ${it.name} because there is no valid configuration")
                    project.logger.lifecycle(
                        """
                        Missing information to configure Secret Signing. Have you
                        executed task 'fetchSecretSigning'?
                        
                        Make sure you execute the 'fetchSecretSigning' then sync project again
                    """.trimIndent()
                    )
                    it.enable = false
                }
            } else {
                if (it.flavorName !in configuredVariants) {
                    project.logger.lifecycle("Disabling ${it.name} because there is no valid configuration")
                    it.enable = false
                }
            }
        }
    }

    companion object {
        fun empty(project: Project): SigningConfigCollection = SigningConfigCollection(emptyList(), project)
    }
}