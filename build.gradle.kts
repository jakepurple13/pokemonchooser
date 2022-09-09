import org.jetbrains.compose.compose
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "com.programmersbox"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("com.google.code.gson:gson:2.9.0")
            }
        }
        val jvmTest by getting
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Exe)
            packageName = "pokemonchooser"
            packageVersion = "1.0.0"

            macOS {
                setDockNameSameAsPackageName = true
                bundleID = "${project.group}.${project.name}"

                packageBuildVersion = "1.0.0"

                signing {
                    sign.set(true)

                }

                notarization {
                    appleID.set("test.app@example.com")
                    password.set("@keychain:NOTARIZATION_PASSWORD")
                }
            }
        }
    }
}

gradle.buildFinished {
    val pkgTasks =
        project.gradle.startParameter.taskNames.filter { it.startsWith("package", ignoreCase = true) }

    val pkgFormat =
        compose.desktop.application.nativeDistributions.targetFormats.firstOrNull { it.isCompatibleWithCurrentOS }
    val nativePkg = buildDir.resolve("compose/binaries").findPkg(pkgFormat?.fileExt)
    val jarPkg = buildDir.resolve("compose/jars").findPkg(".jar")
    nativePkg.ghActionOutput("app_pkg")
    jarPkg.ghActionOutput("uber_jar")
}

fun File.findPkg(format: String?) = when (format != null) {
    true -> walk().firstOrNull { it.isFile && it.name.endsWith(format, ignoreCase = true) }
    else -> null
}

fun File?.ghActionOutput(prefix: String) = this?.let {
    when (System.getenv("GITHUB_ACTIONS").toBoolean()) {
        true -> println(
            """
        ::set-output name=${prefix}_name::${it.name}
        ::set-output name=${prefix}_path::${it.absolutePath}
      """.trimIndent()
        )

        else -> println("$prefix: $this")
    }
}