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

import org.gradle.api.GradleException
import java.io.File

interface SecretSigningExtension {
    var secretName: String?
    var regionName: String?
    var keystoreFile: File?
}
open class SecretSigningExtensionImpl : SecretSigningExtension {


    override var secretName: String? = null
    override var regionName: String? = null
    override var keystoreFile: File? = null

    fun isValid(): Boolean {
        return secretName != null && regionName != null && keystoreFile != null
    }

    fun reportMissingFields(): List<String> = listOf(
        ::secretName,
        ::regionName,
        ::keystoreFile
    ).mapNotNull {
        if (it.get() == null) {
            it.name
        } else {
            null
        }
    }

    override fun toString(): String {
        return "{secretName: $secretName, regionName: $regionName, file: $keystoreFile}"
    }
}

fun SecretSigningExtensionImpl?.orEmpty(): SecretSigningExtensionImpl {
    return this ?: SecretSigningExtensionImpl()
}

fun SecretSigningExtensionImpl?.mergeWith(vararg others: SecretSigningExtensionImpl?): SecretSigningExtensionImpl {
    return mergeValues(this, *others)
}

fun mergeValues(vararg config: SecretSigningExtensionImpl?): SecretSigningExtensionImpl {
    val nonNull = config.filterNotNull().takeUnless { it.isEmpty() }
        ?: throw GradleException(
            """Cannot merge configurations, all objects are null.

               Please make sure you provide configuration of 'secretSigning' extension either on
               'productFlavor' or in base 'android' extension
""".trimIndent()
        )
    return SecretSigningExtensionImpl().also { result ->
        result.secretName = nonNull.firstOrNull { it.secretName != null }?.secretName
        result.regionName = nonNull.firstOrNull { it.regionName != null }?.regionName
        result.keystoreFile = nonNull.firstOrNull { it.keystoreFile != null }?.keystoreFile
    }
}