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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware

class SecretSigningPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        project.pluginManager.withPlugin("com.android.base") {
            val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)

            val applicationExtension = project.extensions.getByType(ApplicationExtension::class.java)
            configureAppExtension(applicationExtension)


            project.tasks.register("fetchSecretSigning", FetchSecretsTask::class.java, applicationExtension)

            val parsedSigningInfo = parseSecretSigningData(project)

            parsedSigningInfo.configureSinging(androidComponents)
        }
    }

    private fun configureAppExtension(
        applicationExtension: ApplicationExtension,
    ) {
        (applicationExtension as? ExtensionAware)?.let {
            it.extensions.create(SecretSigningExtension::class.java, "secretSigning", SecretSigningExtensionImpl::class.java)
        }

        applicationExtension.productFlavors.all { flavor ->
            flavor.extensions.create(SecretSigningExtension::class.java, "secretSigning", SecretSigningExtensionImpl::class.java)
        }

    }
}
