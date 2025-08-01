import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("java-gradle-plugin")
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("maven-publish")
    id("com.gradle.plugin-publish") version "1.2.1"
    id("signing")
}

group = "com.inqbarna"
version = "1.4-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn")
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

dependencies {
//    implementation "org.jetbrains.kotlin:kotlin-stdlib"
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    compileOnly("com.android.tools.build:gradle-api:8.5.1")
    implementation(platform("software.amazon.awssdk:bom:2.32.9"))
    implementation("software.amazon.awssdk:secretsmanager")
    implementation("com.google.guava:guava:33.2.1-jre")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

signing {
    useGpgCmd()
    sign(configurations["archives"])
}

gradlePlugin {
    website = "https://github.com/InQBarna/aws_secret_signing_plugin"
    vcsUrl = "https://github.com/InQBarna/aws_secret_signing_plugin"
    plugins {
        val simplePlugin by creating {
            id = "com.inqbarna.secretsigning"
            implementationClass = "com.inqbarna.secretsigning.SecretSigningPlugin"
            displayName = "AWS Secret Signing Plugin"
            description = "Get passwords from AWS Secret Manager to configure android signing release configuration"
            tags.addAll("android", "signing", "secret", "security", "aws")
        }

        val secretsPlugin by creating {
            id = "com.inqbarna.secrets"
            implementationClass = "com.inqbarna.secrets.SecretsPlugin"
            displayName = "AWS Secrets Plugin"
            description = "Get secrets from AWS Secret Manager to configure android application"
            tags.addAll("android", "secrets", "security", "aws")
        }
    }
}

tasks.named("test", Test::class) {
    useJUnitPlatform()
}
