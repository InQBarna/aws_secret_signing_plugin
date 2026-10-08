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

import com.android.build.api.dsl.ProductFlavor
import org.gradle.api.Action
import org.gradle.api.plugins.ExtensionAware

internal const val SECRET_SIGNING_EXTENSION_NAME = "secretSigning"

/**
 * Configures the `secretSigning` block of this product flavor.
 *
 * Gradle only generates a Kotlin DSL accessor for `android { secretSigning { } }`. Without this helper,
 * `secretSigning { }` written inside a flavor resolves to that outer accessor and silently configures the
 * global block. Importing `com.inqbarna.secretsigning.secretSigning` makes it target the flavor instead.
 */
fun ProductFlavor.secretSigning(action: Action<in SecretSigningExtension>) {
    (this as ExtensionAware).extensions.configure(SECRET_SIGNING_EXTENSION_NAME, action)
}
