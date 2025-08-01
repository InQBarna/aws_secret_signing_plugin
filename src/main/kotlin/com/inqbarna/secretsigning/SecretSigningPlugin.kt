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

package com.inqbarna.secretsigning

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.inqbarna.secrets.SecretsExtension
import com.inqbarna.secrets.SecretsPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.internal.cc.base.logger

class SecretSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.pluginManager.apply(SecretsPlugin::class.java)

        project.pluginManager.withPlugin("com.android.base") {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

            val applicationExtension = project.extensions.getByType(ApplicationExtension::class.java)
            val secretSigningExtension = configureAppExtension(applicationExtension)

            var signingConfigured: ApkSigningConfig? = null
            androidComponents.finalizeDsl { appExtension ->
                if (!secretSigningExtension.isValid()) {
                    val missingFields = secretSigningExtension.reportMissingFields()
                    logger.lifecycle("No secret signing configured, please configure the following fields: ${missingFields.joinToString(", ")}. Release builds will be disabled")
                }

                appExtension.signingConfigs {
                    signingConfigured = this.create("releaseSigning") {
                        it.storeFile = secretSigningExtension.keystoreFile
                        val secrets = project.extensions.getByType(SecretsExtension::class.java)
                        it.storePassword = secrets[secretSigningExtension.keystorePassKey.get()].get()
                        it.keyAlias = secrets[secretSigningExtension.aliasNameKey.get()].get()
                        it.keyPassword = secrets[secretSigningExtension.aliasPasswordKey.get()].get()
                    }
                }
                if (signingConfigured != null) {
                    appExtension.buildTypes.onEach { buildType ->
                        if (buildType.name == "release") {
                            buildType.signingConfig = signingConfigured
                        }
                    }
                }
            }

            androidComponents.beforeVariants(
                selector = androidComponents.selector().withBuildType("release")
            ) { variantBuilder ->
                variantBuilder.enable = signingConfigured != null && secretSigningExtension.isValid()
            }
        }
    }

    private fun configureAppExtension(
        applicationExtension: ApplicationExtension,
    ): SecretSigningExtensionImpl {
        return (applicationExtension as ExtensionAware).extensions.create(SecretSigningExtension::class.java, "secretSigning", SecretSigningExtensionImpl::class.java) as SecretSigningExtensionImpl

    }
}
