/*
 * Copyright 2025 Inqbarna Kenkyuu Jo S.L
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

@file:Suppress("UnstableApiUsage")

package com.inqbarna.secretsigning

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.DslExtension
import com.inqbarna.secrets.SecretsExtension
import com.inqbarna.secrets.SecretsPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class SecretSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(SecretsPlugin::class.java)

        project.pluginManager.withPlugin("com.android.base") {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

            // Register secretSigning { } as a DSL block on android { } (project-level) and on each
            // productFlavor { } block. Values are merged field by field, see SecretSigningExtensionImpl.merge.
            val dslExt = DslExtension.Builder(SECRET_SIGNING_EXTENSION_NAME)
                .extendProjectWith(SecretSigningExtensionImpl::class.java)
                .extendProductFlavorWith(SecretSigningExtensionImpl::class.java)
                .build()

            androidComponents.registerExtension(dslExt) { variantExtConfig ->
                val global = variantExtConfig.projectExtension(SecretSigningExtensionImpl::class.java)
                val flavors = variantExtConfig.productFlavorsExtensions(SecretSigningExtensionImpl::class.java)
                SigningVariantExtension(SecretSigningExtensionImpl.merge(global.toSpec(), flavors.map { it.toSpec() }))
            }

            // Release signing config per flavor combination, keyed by the combination's flavor names
            // (flavor names are unique across dimensions, so a set identifies the combination).
            val signingByFlavors = mutableMapOf<Set<String>, ApkSigningConfig>()

            androidComponents.finalizeDsl { appExt ->
                val secrets = project.extensions.getByType(SecretsExtension::class.java)
                val globalSpec = appExt.secretSigningSpec()

                val dimensions = appExt.flavorDimensions.toList()
                val flavorsByDimension = appExt.productFlavors.groupBy { flavor ->
                    flavor.dimension ?: dimensions.singleOrNull()
                }.filterKeys { it != null }.mapKeys { it.key!! }

                val createdByConfig = mutableMapOf<MergedSigningConfig, ApkSigningConfig>()
                flavorCombinations(dimensions, flavorsByDimension).forEach { combo ->
                    val merged = SecretSigningExtensionImpl.merge(globalSpec, combo.map { it.secretSigningSpec() })
                    val variantLabel = combo.joinToString("") { it.name.replaceFirstChar(Char::titlecase) }
                        .replaceFirstChar(Char::lowercase) + if (combo.isEmpty()) "release" else "Release"
                    if (!merged.isValid()) {
                        project.logger.lifecycle(
                            "SecretSigning: no valid config for '$variantLabel', missing fields: ${merged.reportMissingFields().joinToString()}. It will be disabled."
                        )
                        return@forEach
                    }
                    val signingConfig = createdByConfig.getOrPut(merged) {
                        appExt.signingConfigs.create("${variantLabel}Signing") { sc ->
                            sc.storeFile = merged.keystoreFile
                            sc.storePassword = secrets[merged.keystorePassKey].get()
                            sc.keyAlias = secrets[merged.aliasNameKey].get()
                            sc.keyPassword = secrets[merged.aliasPasswordKey].get()
                        }
                    }
                    project.logger.info("SecretSigning: '$variantLabel' uses signing config '${signingConfig.name}'")
                    signingByFlavors[combo.map { it.name }.toSet()] = signingConfig
                }
            }

            val releaseSelector = androidComponents.selector().withBuildType("release")

            androidComponents.beforeVariants(releaseSelector) { vb ->
                vb.enable = vb.productFlavors.map { it.second }.toSet() in signingByFlavors
            }

            androidComponents.onVariants(releaseSelector) { variant ->
                signingByFlavors[variant.productFlavors.map { it.second }.toSet()]?.let {
                    variant.signingConfig.setConfig(it)
                }
            }
        }
    }

    /** The android { } or productFlavor { } object carries the secretSigning block registered via DslExtension. */
    private fun Any.secretSigningSpec(): SigningSpec =
        (this as ExtensionAware).extensions.findByType(SecretSigningExtensionImpl::class.java)?.toSpec() ?: SigningSpec()
}
