package com.inqbarna.secretsigning

import com.android.build.api.dsl.ApkSigningConfig

internal class SigningConfigVersions {
    // copied from agp-sources: https://github.com/guptadeepanshu/agp-sources/blob/acf46ed4e02469b4601d4d921de7eb4a23a9f100/sources/com.android.tools.build/gradle/com/android/build/gradle/internal/signing/SigningConfigVersions.kt
    companion object {
        private const val serialVersionUID = 1L

        // The lowest API with v2 signing support
        const val MIN_V2_SDK = 24
        // The lowest API with v3 signing support
        const val MIN_V3_SDK = 28
        // The lowest API with v4 signing support
        const val MIN_V4_SDK = 30


        // copied from agp-sources: https://github.com/guptadeepanshu/agp-sources/blob/acf46ed4e02469b4601d4d921de7eb4a23a9f100/sources/com.android.tools.build/gradle/com/android/build/api/variant/impl/SigningConfigImpl.kt
        fun resolveV1Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int): Boolean {
            val enableV1Signing = dslSigningConfig.enableV1Signing
            return when {
                // Sign with v1 if it's enabled explicitly.
                enableV1Signing != null -> enableV1Signing
                // We need v1 if minSdk < MIN_V2_SDK.
                targetApi < MIN_V2_SDK -> true
                // We don't need v1 if minSdk >= MIN_V2_SDK and we're signing with v2.
                resolveV2Enabled(dslSigningConfig, targetApi) -> false
                // We need v1 if we're not signing with v2 and minSdk < MIN_V3_SDK.
                targetApi < MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v1 only if we're not signing with v3.
                else -> !resolveV3Enabled(dslSigningConfig, targetApi)
            }
        }
        fun resolveV2Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int): Boolean {
            val enableV2Signing = dslSigningConfig.enableV2Signing
            return when {
                // Don't sign with v2 if we're installing on a device that doesn't support it.
                targetApi < MIN_V2_SDK -> false
                // Otherwise, sign with v2 if it's enabled explicitly.
                enableV2Signing != null -> enableV2Signing
                // We sign with v2 if minSdk < MIN_V3_SDK, even if we're also signing with v1,
                // because v2 signatures can be verified faster than v1 signatures.
                targetApi < MIN_V3_SDK -> true
                // If minSdk >= MIN_V3_SDK, sign with v2 only if we're not signing with v3.
                else -> !resolveV3Enabled(dslSigningConfig, targetApi)
            }
        }
        fun resolveV3Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int): Boolean {
            return when {
                // Don't sign with v3 if we're installing on a device that doesn't support it.
                targetApi < MIN_V3_SDK -> false
                // Otherwise, sign with v3 if it's enabled explicitly.
                else -> dslSigningConfig.enableV3Signing ?: false
            }
        }
        fun resolveV4Enabled(dslSigningConfig: ApkSigningConfig, targetApi: Int): Boolean {
            return when {
                // Don't sign with v4 if we're installing on a device that doesn't support it.
                targetApi < MIN_V4_SDK -> false
                // Otherwise, sign with v4 if it's enabled explicitly.
                else -> dslSigningConfig.enableV4Signing ?: false
            }
        }
    }
}