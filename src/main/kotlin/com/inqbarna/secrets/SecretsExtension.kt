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

import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.cc.base.logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

interface SecretsExtension {
    val secretName: Property<String>
    val regionName: Property<String>
    operator fun get(name: String): Provider<String>
}

private val sync = Any()

internal abstract class SecretExtensionImpl(private val project: Project, private val providerFactory: ProviderFactory) : SecretsExtension {

    abstract val secretsFile: RegularFileProperty

    private val providersMap: ConcurrentMap<String, Provider<String>> = ConcurrentHashMap()

    init {
        regionName.convention("eu-west-1")
    }

    override operator fun get(name: String): Provider<String> {
        logger.lifecycle("Getting $name")
        return providersMap.computeIfAbsent(name) {
            providerFactory.provider {
                val secretsFile = secretsFile.get().asFile

                var fileFetched = false
                synchronized(sync) {
                    if (!secretsFile.exists()) {
                        logger.lifecycle("File doesn't exist, will download")
                        project.downloadSecretsToFile(secretsFile, secretName.get(), regionName.get())
                        fileFetched = true
                    }
                }
                val decodedSecrets = secretsFile.inputStream().use {
                    SecretJsonFormat.decodeFromStream<Map<String, String>>(it)
                }

                if (name !in decodedSecrets) {
                    if (fileFetched) {
                        throw GradleException("Secret '$name' not found. Make sure you have configured properly the extension. Execute 'refreshSecrets' task to fetch any newly declared secrets.")
                    } else {
                        // try to fetch the file again, just in case secrets are updated (we don't have checksum or versioning to check with the server)
                        synchronized(sync) {
                            logger.lifecycle("File doesn't exist, will download")
                            project.downloadSecretsToFile(secretsFile, secretName.get(), regionName.get())
                        }

                        val newlyDecodedSecrets = secretsFile.inputStream().use {
                            SecretJsonFormat.decodeFromStream<Map<String, String>>(it)
                        }

                        if (name !in newlyDecodedSecrets) {
                            throw GradleException("Secret '$name' not found after fetching the file. Make sure you have configured properly the extension.")
                        }
                        newlyDecodedSecrets[name]
                    }
                } else {
                    decodedSecrets[name]
                }
            }
        }

    }
}
