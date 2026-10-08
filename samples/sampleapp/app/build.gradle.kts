import com.inqbarna.secretsigning.SecretSigningExtension
// Typed helper so that secretSigning { } inside a product flavor configures that flavor (not the global block)
import com.inqbarna.secretsigning.secretSigning

plugins {
    id("com.android.application")
    id("com.inqbarna.secretsigning")
}

android {
    namespace = "com.example.sampleapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.sampleapp"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += listOf("env", "tier")
    productFlavors {
        create("demo") {
            dimension = "env"
            applicationIdSuffix = ".demo"
            // Explicit form, equivalent to the imported secretSigning { } helper
            extensions.configure<SecretSigningExtension>("secretSigning") {
                keystoreFile = rootProject.file("keystores/demo.jks")
                keystorePassKey.set("demo_store_pass")
                aliasNameKey.set("demo_alias_name")
                aliasPasswordKey.set("demo_alias_pass")
            }
        }
        create("production") {
            dimension = "env"
            // Only the keystore: key names come from the global secretSigning block below
            secretSigning {
                keystoreFile = rootProject.file("keystores/production.jks")
            }
        }
        create("staging") {
            // No keystoreFile here nor globally: stagingFreeRelease / stagingPaidRelease are disabled
            dimension = "env"
        }
        create("free") {
            dimension = "tier"
        }
        create("paid") {
            dimension = "tier"
            // Lower priority than env: only applies where env does not set aliasNameKey
            secretSigning {
                aliasNameKey.set("paid_alias_name")
            }
        }
    }

    // Global defaults, inherited field by field by every flavor combination
    secretSigning {
        keystorePassKey.set("sample_store_pass")
        aliasNameKey.set("sample_alias_name")
        aliasPasswordKey.set("sample_alias_pass")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

secrets {
    secretName.set("my-app/signing")
    regionName.set("eu-west-1")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
}
