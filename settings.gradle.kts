import org.gradle.api.GradleException

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
        // Flutter plugin repository
        maven("https://storage.flutter-io.cn/download.flutter.io")
    }
}
dependencyResolutionManagement {
    // PREFER_PROJECT 避免 Flutter 等插件在 build.gradle 里添加仓库时刷屏警告
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    val storageUrl: String = "https://storage.flutter-io.cn"

    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.google.com/")
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("$storageUrl/download.flutter.io")
        maven("https://repo1.maven.org/maven2/")
    }
}

rootProject.name = "myagent"
include(":app")
include(":assists")
val filePath = settingsDir.toString() + "/ui/.android/include_flutter.groovy"
apply(from = File(filePath))

include(":baselib")
include(":accessibility")
include(":omniintelligence")

fun requireOmniInferModule(moduleName: String, moduleDir: File, markerFileName: String) {
    if (File(moduleDir, markerFileName).exists()) {
        return
    }

    throw GradleException(
        """
        Missing required OmniInfer sources for $moduleName at: ${moduleDir.relativeTo(settingsDir)}

        Initialize the required submodules with:
          git submodule update --init third_party/omniinfer
        """.trimIndent()
    )
}

// omniinfer-server - commented out (submodule not initialized)
// val omniInferServerDir = File(settingsDir, "third_party/omniinfer/android/omniinfer-server")
// val omniInferServerMarker = File(omniInferServerDir, "build.gradle.kts")
// val omniInferTasksRequested = gradle.startParameter.taskNames.any { taskName ->
//     val normalized = taskName.substringAfterLast(':')
//     normalized.contains("omniinfer", ignoreCase = true) ||
//         normalized in setOf("build", "assemble", "check", "test")
// }
//
// if (omniInferServerMarker.exists() || omniInferTasksRequested) {
//     requireOmniInferModule(":omniinfer-server", omniInferServerDir, "build.gradle.kts")
//     include(":omniinfer-server")
//     project(":omniinfer-server").projectDir = omniInferServerDir
// }
include(":uikit")
include(":core:main")
project(":core:main").projectDir = File(settingsDir, "ReTerminal/core/main")
include(":core:components")
project(":core:components").projectDir = File(settingsDir, "ReTerminal/core/components")
include(":core:resources")
project(":core:resources").projectDir = File(settingsDir, "ReTerminal/core/resources")
include(":core:terminal-emulator")
project(":core:terminal-emulator").projectDir = File(settingsDir, "ReTerminal/core/terminal-emulator")
include(":core:terminal-view")
project(":core:terminal-view").projectDir = File(settingsDir, "ReTerminal/core/terminal-view")
