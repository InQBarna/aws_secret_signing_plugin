### Gradle plugin to sign android release builds with data from AWS

Store your signing passwords on AWS Secret Manager safely, then apply the plugin
to fetch them and configure signing settings for release builds in your local builds

### Requirements

You will need to have AWS CLI [installed](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
and [configured](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-quickstart.html#getting-started-quickstart-new) though on your computer

### Configuration

In your project `build.gradle` apply the *Secret Signing* plugin

<details open>
<summary>Plugin DSL</summary>

```groovy
plugins {
    id "com.inqbarna.secretsigning" version "1.0"
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
    classpath "com.inqbarna:secretsigning:1.0"
  }
}

apply plugin: "com.inqbarna.secretsigning"
```
</details>


Configure it in the `secretSigning` block as follows

```groovy
secretSigning {
    // This is the secret name as declared in AWS Secret Manager
    secretName "your/aws/secret/name"
    // The zone where to fetch the secret (it must be deployed there too)
    regionName "eu-west-1"
    
    // Local path for the keystore. If you use strong passwords for keystore and for alias
    // it may be safe to commit the file to your repository
    keystoreFile file("keystore_filename.jks")
}
```

### AWS Secret format

The plugin expects a key/value list describing information to enable *release* signing.

The expected structure of the secret is:

```json
{
  "alias_name": "<alias_name_to_use>",
  "alias_pass": "<your_alias_pass>",
  "store_pass": "<your_keystore_pass>"
}
```
