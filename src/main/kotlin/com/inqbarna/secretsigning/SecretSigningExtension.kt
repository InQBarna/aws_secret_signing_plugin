package com.inqbarna.secretsigning

import java.io.File

interface SecretSigningExtension {
    var secretName: String?
    var regionName: String?
    var keystoreFile: File?
    var targetBuildType: String?
}