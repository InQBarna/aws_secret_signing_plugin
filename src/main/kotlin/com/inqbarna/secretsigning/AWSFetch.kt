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
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.Sets
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.configurationcache.extensions.capitalized
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.*
import java.io.File
import javax.inject.Inject


// Use this code snippet in your app.
// If you need more information about configurations or implementing the sample code, visit the AWS docs:
// https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites

// Use this code snippet in your app.
// If you need more information about configurations or implementing the sample code, visit the AWS docs:
// https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites
private fun getSecret(secretName: String, regionName: String): SecretInfo {
    val region: Region = Region.of(regionName)

    // Create a Secrets Manager client
    val client = SecretsManagerClient.builder()
        .region(region)
        .build()

    // In this sample we only handle the specific exceptions for the 'GetSecretValue' API.
    // See https://docs.aws.amazon.com/secretsmanager/latest/apireference/API_GetSecretValue.html
    // We rethrow the exception by default.
    val getSecretValueRequest = GetSecretValueRequest.builder()
        .secretId(secretName)
        .build()
    val getSecretValueResponse: GetSecretValueResponse = try {
        client.getSecretValue(getSecretValueRequest)
    } catch (e: DecryptionFailureException) {
        // Secrets Manager can't decrypt the protected secret text using the provided KMS key.
        // Deal with the exception here, and/or rethrow at your discretion.
        throw GradleException("Failed to get AWS secret", e)
    } catch (e: InternalServiceErrorException) {
        // An error occurred on the server side.
        // Deal with the exception here, and/or rethrow at your discretion.
        throw GradleException("Failed to get AWS secret", e)
    } catch (e: InvalidParameterException) {
        // You provided an invalid value for a parameter.
        // Deal with the exception here, and/or rethrow at your discretion.
        throw GradleException("Failed to get AWS secret", e)
    } catch (e: InvalidRequestException) {
        // You provided a parameter value that is not valid for the current state of the resource.
        // Deal with the exception here, and/or rethrow at your discretion.
        throw GradleException("Failed to get AWS secret", e)
    } catch (e: ResourceNotFoundException) {
        // We can't find the resource that you asked for.
        // Deal with the exception here, and/or rethrow at your discretion.
        throw GradleException("Failed to get AWS secret.  Did you set 'secretSigning.secretName' to a proper value?", e)
    } catch (e: SdkClientException) {
        throw GradleException("Failed to get AWS credentials, did you configure them properly? See https://github.com/awsdocs/aws-doc-sdk-examples about how to use CLI to init them.\nInstall the CLI and run 'aws configure' with values from AWS Security Credentials page", e)
    }

    // Decrypts secret using the associated KMS key.
    // Depending on whether the secret is a string or binary, one of these fields will be populated.
    return if (getSecretValueResponse.secretString() != null) {
        val result = runCatching {
            Json.decodeFromString<SecretInfo>(getSecretValueResponse.secretString())
        }

        if (result.isSuccess) {
            result.getOrThrow()
        } else {
            throw GradleException("Did you store secret properly? Known fields on secret are 'alias_name', 'alias_pass', 'store_pass'")
        }
    } else {
        throw GradleException("Binary secret not supported by the plugin!")
//        decodedBinarySecret =
//            String(Base64.getDecoder().decode(getSecretValueResponse.secretBinary().asByteBuffer()).array())
    }
}

internal abstract class FetchSecretsTask @Inject constructor(
    @Internal val androidComponentsExtension: ApplicationExtension
    ) : DefaultTask() {

    init {
        description = "Fetch secret signing information from AWS"
    }

    @OptIn(ExperimentalSerializationApi::class)
    @TaskAction
    fun fetchSecrets() {

        val defaultExtension = (androidComponentsExtension as? ExtensionAware)
            ?.let { it.extensions.findByType(SecretSigningExtension::class.java) as SecretSigningExtensionImpl? }.orEmpty()



        val taskPieces = if (androidComponentsExtension.productFlavors.isNotEmpty()) {
            val multiMapBuilder = ImmutableSetMultimap.builder<String, FetchTaskPiece>()
            androidComponentsExtension.productFlavors.forEach { flavor ->
                val merged =
                    (flavor.extensions.findByType(SecretSigningExtension::class.java) as SecretSigningExtensionImpl?).mergeWith(
                        defaultExtension
                    )
                multiMapBuilder.put(flavor.dimension!!, FetchTaskPiece(flavor.name, merged))
//            logger.error("Adding entry(${flavor.dimension!!} = $merged")
            }
            val multimap = multiMapBuilder.build()
            val uncombinedPieces = androidComponentsExtension.flavorDimensions.map {
                multimap[it]
            }

            Sets.cartesianProduct(uncombinedPieces).mapNotNull {
                runCatching {
                    it.merge()
                }.recover {
                    logger.warn(it.message)
                    null
                }.getOrThrow()
            }
        } else {
            if (!defaultExtension.isValid()) {
                throw GradleException(
                    """
                        Missing information to fetch default 'secretSigning'.
                        Please configure properly 'secretSigning' extension at flavor or android extension level.
                
                        Missing fields: ${defaultExtension.reportMissingFields().joinToString()}    
                    """.trimIndent()
                )
            }
            listOf(FetchTaskPiece("release", defaultExtension))
        }



        if (taskPieces.isEmpty()) {
            logger.error("There's no 'secretSigning' configuration to fetch")
        } else {
            val data = taskPieces
                .onEach {
                    logger.info("Fetching secret for '${it.name}' using params ${it.config}")
                }
                .map {
                    SignConfigSecret(
                        "release",
                        it.name,
                        it.config.keystoreFile!!.absolutePath,
                        "${it.name}Signing",
                        getSecret(it.config.secretName!!, it.config.regionName!!)
                    )
                }

            getSigningDataFile(project).outputStream().use {
                val json = Json {
                    this.prettyPrint = true
                }
                json.encodeToStream(data, it)
            }
        }

        logger.lifecycle("Secrets have been fetched! Signing configs only regenerate on 'gradle sync' thus, sync your project again to configure properly")
    }
}
data class FetchTaskPiece(
    val name: String,
    val config: SecretSigningExtensionImpl
)

fun Iterable<FetchTaskPiece>.merge(): FetchTaskPiece {
    val name = mapIndexed { index: Int, fetchTaskPiece: FetchTaskPiece ->
        if (index != 0) fetchTaskPiece.name.capitalized() else fetchTaskPiece.name
    }.joinToString(separator = "")
    val configs = map { it.config }.toTypedArray()
    val resultConfig = mergeValues(*configs)
    if (!resultConfig.isValid()) {
        throw GradleException(
            """
                Missing information on flavor $name. Please configure properly 'secretSigning' extension
                at flavor or android extension level.
                
                Missing fields: ${resultConfig.reportMissingFields().joinToString()}
            """.trimIndent()
        )
    }
    return FetchTaskPiece(
        name,
        resultConfig
    )
}

@OptIn(ExperimentalSerializationApi::class)
internal fun parseSecretSigningData(project: Project): SigningConfigCollection {
    val secretSignFile = getSigningDataFile(project)
    return if (secretSignFile.exists()) {
        runCatching {
            val secrets = secretSignFile.inputStream().use {
                Json.decodeFromStream<List<SignConfigSecret>>(it)
            }
            SigningConfigCollection(secrets, project)
        }.recover {
            project.logger.error("Failed to parse secret Signing file, will fall back to defaults")
            SigningConfigCollection.empty(project)
        }.getOrThrow()
    } else {
        SigningConfigCollection.empty(project)
    }
}
internal fun getSigningDataFile(project: Project): File {
    val directory = project.layout.buildDirectory.get().dir("secret_signing")
    val dirFile = directory.asFile
    if (!dirFile.exists()) {
        dirFile.mkdirs()
    }
    return File(dirFile, "signing.dat")
}
