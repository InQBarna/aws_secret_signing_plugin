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
import kotlinx.serialization.json.decodeFromStream
import org.gradle.api.GradleException
import java.io.File

/** Gradle property pointing to a local JSON secrets file, used instead of AWS Secrets Manager when set. */
const val LOCAL_SECRETS_FILE_PROPERTY = "inqbarna.secrets.localFile"

/** Source of the flat key/value map stored in a secret. */
internal fun interface SecretFetcher {
    fun fetch(secretName: String, regionName: String): Map<String, String>
}

internal object AwsSecretFetcher : SecretFetcher {
    override fun fetch(secretName: String, regionName: String): Map<String, String> =
        getSecret<Map<String, String>>(secretName, regionName)
}

/** Reads the secret from a local JSON file with the same shape as the AWS secret. Name and region are ignored. */
internal class LocalFileSecretFetcher(private val file: File) : SecretFetcher {
    override fun fetch(secretName: String, regionName: String): Map<String, String> {
        if (!file.isFile) {
            throw GradleException("Local secrets file '$file' not found. Check the '$LOCAL_SECRETS_FILE_PROPERTY' Gradle property.")
        }
        return try {
            file.inputStream().use { SecretJsonFormat.decodeFromStream<Map<String, String>>(it) }
        } catch (e: Exception) {
            throw GradleException("Local secrets file '$file' must be a flat JSON object of string values, e.g. {\"store_pass\": \"...\"}", e)
        }
    }
}
