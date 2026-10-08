### Gradle plugin import secrets from AWS Secret manager

# Secrets Plugin

The secrets plugin is a generic secrets fetch from AWS Secret Manager and exposes them as
properties through the `secrets` extension

## Requirements

You will need to have AWS CLI [installed](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
and [configured](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html#getting-started-quickstart-new) though on your computer
(not needed when using a [local secrets file](#local-secrets-file))

## Setup

In your project's `build.gradle` apply the *Secrets* plugin as follows:

<details open>
<summary>Plugin DSL</summary>

```kotlin
plugins {
    id("com.inqbarna.secrets") version "1.5"
}
```
</details>
<details>
<summary>Legacy Syntax</summary>

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "com.inqbarna:secretsigning:1.5"
  }
}

apply plugin: "com.inqbarna.secrets"
```
</details>


## Usage

You need to configure the `secrets` extension in your `build.gradle` file. The plugin will fetch the secrets
when appropriate.

```kotlin
secrets {
    // This is the secret name as declared in AWS Secret Manager
    secretName = "your/aws/secret/default"
    // The zone where to fetch the secret (it must be deployed there too). By default if not specified `eu-west-1` is used
    regionName = "eu-west-1"
}
```

Then you can start using your secrets **NOT BEFORE** the `afterEvaluate` block of the build gradle, or in the `finalizeDsl` block of `androidComponents`

Also properties generated are lazy, and appropriate to feed tasks `@Input` _Properties.

For example you can have your MAPS api key in AWS then use this block to configure the manifest.

```kotlin
androidComponents {
    finalizeDsl {
        it.defaultConfig {
            manifestPlaceholders["MAPS_API_KEY"] = secrets["maps_api_key"].get()
        }
    }
}
```

### Local secrets file

To read secrets from a local JSON file instead of AWS (useful for CI and local testing), set the
`inqbarna.secrets.localFile` Gradle property, for example in `gradle.properties`. The path is relative to the
root project directory, and an empty value means AWS is used. The file has the same flat shape as the AWS secret, and `secretName` becomes optional.
Secrets are still cached in `build/secrets/secrets.json`.

```properties
inqbarna.secrets.localFile=secrets/local-secrets.json
```

> **Refreshing the cache.** The cache does not record where the secrets came from, nor does it check whether they
> changed. A key that is missing from the cache triggers one re-download. Existing keys are reused as they are. So
> whenever you know the secrets changed, run `gradle refreshSecrets` (or `clean`), including when you switch between
> a local file and AWS. Otherwise the previously cached values keep being used.

# Secret Signing Plugin

Store your signing passwords on AWS Secret Manager safely, then apply the plugin
to fetch them and configure signing settings for release builds in your local builds

### Configuration

In your project `build.gradle` apply the *Secret Signing* plugin

<details open>
<summary>Plugin DSL</summary>

```groovy
plugins {
    id "com.inqbarna.secretsigning" version "1.5"
}
```
</details>

<details>
<summary>Legacy Syntax</summary>

```groovy
buildscript {
  repositories {
    maven {
      url "https://plugins.gradle.org/m2/"
    }
  }
  dependencies {
    classpath "com.inqbarna:secretsigning:1.5"
  }
}

apply plugin: "com.inqbarna.secretsigning"
```
</details>


The *Secret Signing* plugin applies the *Secrets* plugin itself, so configure the `secrets { }` extension as described
above (or use a [local secrets file](#local-secrets-file)).

You can configure it with the `secretSigning` block in the `android` block (global settings for all variants), or
in each `productFlavors` entry (see [Product flavors](#product-flavors)), with the following options.

```kotlin
android {
    secretSigning {
        // The key for the secret within [Secrets Plugin]. By default it is "store_pass"
        keystorePassKey.set("store_pass")

        // The key for the secret within [Secrets Plugin]. By default it is "alias_name"
        aliasNameKey.set("alias_name")

        // The key for the secret within [Secrets Plugin]. By default it is "alias_pass"
        aliasPasswordKey.set("alias_pass")

        // The path to the keystore file. This is the file that will be used to sign
        keystoreFile = file("keystore_filename.jks")
    }
}
```

### Product flavors

Each product flavor can have its own `secretSigning` block. In Kotlin DSL there are two equivalent ways to write it.

**Option 1: import the typed helper**, then use the short syntax inside the flavor:

```kotlin
import com.inqbarna.secretsigning.secretSigning

android {
    flavorDimensions += listOf("env")
    productFlavors {
        create("production") {
            dimension = "env"
            secretSigning {
                keystoreFile = file("production.jks")
            }
        }
    }
}
```

**Option 2: use the explicit extension syntax**, with no helper import:

```kotlin
import com.inqbarna.secretsigning.SecretSigningExtension

android {
    flavorDimensions += listOf("env")
    productFlavors {
        create("production") {
            dimension = "env"
            extensions.configure<SecretSigningExtension>("secretSigning") {
                keystoreFile = file("production.jks")
            }
        }
    }
}
```

> **Warning:** do not write `secretSigning { }` inside a flavor without the
> `import com.inqbarna.secretsigning.secretSigning` line. Gradle only generates a Kotlin DSL accessor for the global
> `android { secretSigning { } }` block. Inside a flavor, that accessor is still reachable through the enclosing
> `android` block. The build compiles, but every flavor silently writes the **global** block (the last one wins), so
> variants can be signed with the wrong keystore. The helper import makes `secretSigning { }` target the flavor
> instead. The global `secretSigning { }` keeps working with or without the import.

Settings are merged field by field for each combination of flavors. For each field
(`keystoreFile`, `keystorePassKey`, `aliasNameKey`, `aliasPasswordKey`) the highest-priority flavor that sets it wins.
Priority follows the `flavorDimensions` order, as in AGP. If no flavor sets it, the global block is used, and then the
defaults. A release variant whose merged configuration has no `keystoreFile` is disabled.

## Full example with minimal setup


```kotlin
plugins {
    id("com.android.application")
    // Also applies com.inqbarna.secrets, so the secrets { } extension is available
    id("com.inqbarna.secretsigning") version "1.5"
}

secrets {
    secretName = "my-aws-secret"
}

android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.app"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    secretSigning {
        // With secretSigning you can create a strong password for each the keystore and another for the alias
        // then commit the file to your repository
        // you don't need to handle or remember the passwords, they are fetched from AWS Secret Manager
        keystoreFile = file("my-release-key.jks")
    }
}

/// Also you can leverage to the `secrets` extension to fetch other secrets.
androidComponents {
    finalizeDsl {
        it.defaultConfig {
            manifestPlaceholders["MAPS_API_KEY"] = secrets["maps_api_key"].get()
        }
    }
}
```

The plugin expects a key/value list describing information to enable *release* signing.

The expected structure of the secret is:

```json
{
  "alias_name": "<alias_name_to_use>",
  "alias_pass": "<your_alias_pass>",
  "store_pass": "<your_keystore_pass>",
  "maps_api_key": "<your_maps_api_key>"
}
```

# Sample app

An offline harness for the Secret Signing plugin, with per-flavor and multi-dimension signing, lives in
[samples/sampleapp](samples/sampleapp/README.md). It uses the plugin from this repository and a local secrets file, so no AWS access is needed.
