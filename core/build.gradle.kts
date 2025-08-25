import org.gradle.api.provider.Provider

plugins {
    alias(libs.plugins.agp.lib)
}

android {
    namespace = "org.lsposed.lspd.core"

    buildFeatures {
        androidResources = false
        buildConfig = true
    }

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")

        val verName: String by rootProject.extra
        val verCode: Int by rootProject.extra
        buildConfigField("String", "FRAMEWORK_NAME", "\"${rootProject.name}\"")
        buildConfigField("String", "VERSION_NAME", "\"$verName\"")
        buildConfigField("long", "VERSION_CODE", "$verCode")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }
}

val verName: String by rootProject.extra
val verCode: Int by rootProject.extra
val rootDir: Provider<String> = layout.projectDirectory.asFile.map { it.absolutePath }
tasks.register("copyTemplate") {
    dependsOn("preBuild")
    doLast {
        copy {
            from("src/main/jni/template/") {
                expand("VERSION_CODE" to "$verCode", "VERSION_NAME" to verName)
            }
            into("src/main/jni/src/")
        }
    }
}

dependencies {
    api(libs.libxposed.api)
    implementation(projects.apache)
    implementation(projects.axml)
    implementation(projects.hiddenapi.bridge)
    implementation(projects.services.daemonService)
    implementation(projects.services.managerService)
    compileOnly(libs.androidx.annotation)
    compileOnly(projects.hiddenapi.stubs)
}
