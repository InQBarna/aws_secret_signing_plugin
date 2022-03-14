package com.inqbarna.secretsigning

import com.google.gson.GsonBuilder
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.*
import java.io.File
import javax.inject.Inject

interface SignerParams : WorkParameters, java.io.Serializable {
    val inputApk: RegularFileProperty
    val outApk: RegularFileProperty
    var config: SecureSigningParams
    var apkSignerPath: File
}

internal abstract class AWSSignWorker @Inject constructor(
    private val params: SignerParams,
    private val execOperations: ExecOperations
) : WorkAction<SignerParams> {

    val config: SecureSigningParams
        get() = params.config

    private val logger by lazy { Logging.getLogger(AWSSignWorker::class.java) }

    @OptIn(ExperimentalStdlibApi::class)
    override fun execute() {

        val storeFile = params.config.storeFile.absolutePath

        val inFile = params.inputApk.asFile.get().absolutePath
        val outFile = params.outApk.asFile.get().absolutePath

        logger.info("Fetching secret... ${params.config.secretName}")
        val secretInfo = getSecret(params.config.secretName, params.config.regionName)
        logger.debug("Got secret...")

        execOperations.exec {
            it.executable = params.apkSignerPath.absolutePath
            it.args = buildList {
                add("sign")
                add("-v")
                if (config.enabledV1) {
                    add("--v1-signing-enabled")
                }
                if (config.enabledV2) {
                    add("--v2-signing-enabled")
                }
                if (config.enabledV3) {
                    add("--v3-signing-enabled")
                }
                if (config.enabledV4) {
                    add("--v4-signing-enabled")
                }
                add("--in")
                add(inFile)
                add("--out")
                add(outFile)
                add("--ks")
                add(storeFile)
                add("--ks-key-alias")
                add(secretInfo.alias_name)
                add("--ks-pass")
                add("pass:${secretInfo.store_pass}")
                add("--key-pass")
                add("pass:${secretInfo.alias_pass}")
            }
        }.assertNormalExitValue()
        logger.info("Signed $inFile to $outFile")
    }
}


// Use this code snippet in your app.
// If you need more information about configurations or implementing the sample code, visit the AWS docs:
// https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites

// Use this code snippet in your app.
// If you need more information about configurations or implementing the sample code, visit the AWS docs:
// https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/java-dg-samples.html#prerequisites
fun getSecret(secretName: String, regionName: String): SecretInfo {
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