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

import com.android.build.api.variant.VariantExtension
import org.gradle.api.provider.Property
import java.io.File

interface SecretSigningExtension {
    val keystorePassKey: Property<String>
    val aliasNameKey: Property<String>
    val aliasPasswordKey: Property<String>
    var keystoreFile: File?
}

data class MergedSigningConfig(
    val keystoreFile: File?,
    val keystorePassKey: String,
    val aliasNameKey: String,
    val aliasPasswordKey: String
) {
    fun isValid() = keystoreFile != null
    fun reportMissingFields(): List<String> = if (keystoreFile == null) listOf("keystoreFile") else emptyList()
}

class SigningVariantExtension(val config: MergedSigningConfig) : VariantExtension

/**
 * Gradle-free snapshot of one `secretSigning { }` block. A `null` field means "not set here, inherit it".
 */
internal data class SigningSpec(
    val keystoreFile: File? = null,
    val keystorePassKey: String? = null,
    val aliasNameKey: String? = null,
    val aliasPasswordKey: String? = null
)

/**
 * No conventions on purpose: an unset property must stay unset so that values from lower priority
 * levels (global block, then defaults) can be inherited. Defaults are applied by [merge].
 */
abstract class SecretSigningExtensionImpl : SecretSigningExtension, VariantExtension {

    internal fun toSpec() = SigningSpec(
        keystoreFile = keystoreFile,
        keystorePassKey = keystorePassKey.orNull,
        aliasNameKey = aliasNameKey.orNull,
        aliasPasswordKey = aliasPasswordKey.orNull
    )

    companion object {
        const val DEFAULT_STORE_PASS_KEY = "store_pass"
        const val DEFAULT_ALIAS_NAME_KEY = "alias_name"
        const val DEFAULT_ALIAS_PASS_KEY = "alias_pass"

        /**
         * Field-wise merge: for each field, the first flavor (highest priority first) that sets it wins,
         * then the global block, then the default value.
         */
        internal fun merge(global: SigningSpec?, flavorsByPriority: List<SigningSpec>): MergedSigningConfig {
            val levels = flavorsByPriority + listOfNotNull(global)
            fun <T : Any> pick(field: (SigningSpec) -> T?): T? = levels.firstNotNullOfOrNull(field)
            return MergedSigningConfig(
                keystoreFile = pick { it.keystoreFile },
                keystorePassKey = pick { it.keystorePassKey } ?: DEFAULT_STORE_PASS_KEY,
                aliasNameKey = pick { it.aliasNameKey } ?: DEFAULT_ALIAS_NAME_KEY,
                aliasPasswordKey = pick { it.aliasPasswordKey } ?: DEFAULT_ALIAS_PASS_KEY
            )
        }
    }
}

/**
 * All flavor combinations, one flavor per dimension, each ordered by [dimensions] (which is also AGP's
 * flavor priority order). With no dimensions there is a single empty combination.
 */
internal fun <F> flavorCombinations(dimensions: List<String>, flavorsByDimension: Map<String, List<F>>): List<List<F>> =
    dimensions.fold(listOf(emptyList())) { acc, dimension ->
        val flavors = flavorsByDimension[dimension].orEmpty()
        acc.flatMap { combo -> flavors.map { combo + it } }
    }
