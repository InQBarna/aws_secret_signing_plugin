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

package com.inqbarna.secrets

import com.inqbarna.secretsigning.getSecret
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File


class SecretsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val secretsFileTarget = target.layout.buildDirectory.dir("secrets").map { it.file("secrets.json") }

        val secretExt: SecretExtensionImpl = target.extensions.create(SecretsExtension::class.java, "secrets", SecretExtensionImpl::class.java, target, target.providers) as SecretExtensionImpl
        secretExt.secretsFile.set(secretsFileTarget)

        target.tasks.register("refreshSecrets", RefreshSecretsTask::class.java) { task ->
            task.secretsFile.set(secretsFileTarget)
            task.secretsExtension.set(secretExt)
        }
    }
}

internal abstract class RefreshSecretsTask : DefaultTask() {

    @get:OutputFile
    abstract val secretsFile: RegularFileProperty

    @get:Internal
    abstract val secretsExtension: Property<SecretsExtension>

    init {
        // this task is always run, even if the output file is up to date, should be used to refresh secrets
        group = "secrets"
        description = "Refresh secrets from secrets file"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun downloadSecrets() {
        logger.lifecycle("Starting download of secrets...")
        // Logic to download secrets from a remote source
        // This is a placeholder for the actual implementation
        val secretsFile = secretsFile.get().asFile
        val ext = secretsExtension.get()
        project.downloadSecretsToFile(secretsFile, ext.secretName.get(), ext.regionName.get())
    }
}

internal val SecretJsonFormat = Json {
    prettyPrint = true
}

internal fun Project.downloadSecretsToFile(destination: File, secretName: String, regionName: String) {
    val parent = destination.parentFile
    if (parent != null && !parent.exists()) {
        parent.mkdirs()
    }
    // Simulate downloading secrets and writing to the file
    logger.lifecycle("Downloading secrets from AWS: $secretName @ region $regionName")
    val data = getSecret<Map<String, String>>(secretName, regionName)

    destination.outputStream().use {
        SecretJsonFormat.encodeToStream(data, it)
    }
}
