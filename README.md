### Gradle plugin import secrets from AWS Secret manager

# Secrets Plugin

The secrets plugin is a generic secrets fetch from AWS Secret Manager and exposes them as
properties through the `secrets` extension

## Requirements

You will need to have AWS CLI [installed](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
and [configured](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html#getting-started-quickstart-new) though on your computer

## Setup

In your project's `build.gradle` apply the *Secrets* plugin as follows:

<details open>
<summary>Plugin DSL</summary>
```kotlin
plugins {
    id("com.inqbarna.secrets" version) version "1.4"
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
    classpath "com.inqbarna:secretsigning:1.4"
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

# Secret Signing Plugin

Store your signing passwords on AWS Secret Manager safely, then apply the plugin
to fetch them and configure signing settings for release builds in your local builds

### Configuration

In your project `build.gradle` apply the *Secret Signing* plugin

<details open>
<summary>Plugin DSL</summary>

```groovy
plugins {
    id "com.inqbarna.secretsigning" version "1.4"
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
    classpath "com.inqbarna:secretsigning:1.4"
  }
}

apply plugin: "com.inqbarna.secretsigning"
```
</details>


You can configure it in the `secretSigning` in the android block with the following options. 
productFlavors

```kotlin
android {
    secretSigning {
        // The key for the secret within [Secrets Plugin]. By default it is "store_pass"
        keystorePassKey = "store_pass"
        
        // The key for the secret within [Secrets Plugin]. By default it is "alias_name"
        aliasNameKey = "alias_name"
        
        // The key for the secret within [Secrets Plugin]. By default it is "alias_pass"
        aliasPasswordKey = "alias_pass"
        
        // The path to the keystore file. This is the file that will be used to sign
        keystoreFile = file("keystore_filename.jks")
    }
}
```

## Full example with minimal setup


```kotlin
plugins {
    id("com.android.application")
    id("com.inqbarna.secrets") version "1.4"
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
