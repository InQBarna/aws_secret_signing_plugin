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

import com.google.gson.GsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.*
import java.io.File
import javax.inject.Inject
import kotlin.reflect.KProperty1


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
            GsonBuilder()
                .create()
                .fromJson(getSecretValueResponse.secretString(), SecretInfo::class.java)
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

data class SecretInfo(
    val alias_name: String,
    val alias_pass: String,
    val store_pass: String
)

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

internal abstract class FetchSecretsTask @Inject constructor(
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

        if (!file.exists()) {
            throw GradleException("Illegal secret signing configuration. Keystore file '$file' doesn't exist!")
        }

        val secretInfo = getSecret(secretName, regionName)
        val gson = GsonBuilder().create()
        getSigningDataFile(project).outputStream().writer().use {
            gson.toJson(
                SignConfigSecret(
                    "release",
                    file.absolutePath,
                    "secretSigning",
                    secretInfo
                ),
                SignConfigSecret::class.java,
                it
            )
        }

        logger.lifecycle("Secrets have been fetched! Signing configs only regenerate on 'gradle sync' thus, sync your project again to configure properly")
    }
}

internal fun parseSecretSigningData(project: Project): SignConfigSecret? {
    val secretSignFile = getSigningDataFile(project)
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
internal fun getSigningDataFile(project: Project): File {
    val directory = project.layout.buildDirectory.get().dir("secret_signing")
    val dirFile = directory.asFile
    if (!dirFile.exists()) {
        dirFile.mkdirs()
    }
    return File(dirFile, "signing.dat")
}