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

import org.gradle.api.provider.Property
import java.io.File

interface SecretSigningExtension {
    val keystorePassKey: Property<String>
    val aliasNameKey: Property<String>
    val aliasPasswordKey: Property<String>
    var keystoreFile: File?
}
abstract class SecretSigningExtensionImpl : SecretSigningExtension {

    init {
        keystorePassKey.convention("store_pass")
        aliasPasswordKey.convention("alias_pass")
        aliasNameKey.convention("alias_name")
    }

    fun isValid(): Boolean {
        return keystorePassKey.isPresent && aliasPasswordKey.isPresent && aliasNameKey.isPresent && keystoreFile != null
    }

    fun reportMissingFields(): List<String> = listOf(
        ::keystorePassKey,
        ::aliasNameKey,
        ::aliasPasswordKey
    ).mapNotNull {
        if (!(it.get().isPresent)) {
            it.name
        } else {
            null
        }
    }.let {
        if (keystoreFile == null) {
            it + "keystoreFile"
        } else {
            it
        }
    }
}
