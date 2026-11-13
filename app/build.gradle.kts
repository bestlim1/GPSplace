import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

// MAPS_API_KEY는 커밋하지 않는 local.properties에서 읽는다.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.example.gpsplace"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.gpsplace"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    // maps-compose 9.x가 JVM 17 바이트코드로 배포되어 인라인하려면 17이 필요하다
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// 분석서 Won't 인수 조건(FR-08·FR-09): 병합된 매니페스트에 이 권한이 있으면 빌드를 실패시킨다.
// com.google.android.gms.permission.ACTIVITY_RECOGNITION처럼 다른 접두사로 들어와도 잡는다.
val forbiddenPermissions = listOf(
    "BODY_SENSORS",
    "ACTIVITY_RECOGNITION",
    "BLUETOOTH_CONNECT",
    "CALL_PHONE",
    "SEND_SMS",
)

abstract class CheckForbiddenPermissionsTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:Input
    abstract val forbidden: ListProperty<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val androidNs = "http://schemas.android.com/apk/res/android"
        val document = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(mergedManifest.get().asFile)
        val declared = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
            val nodes = document.getElementsByTagName(tag)
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttributeNS(androidNs, "name") }
        }
        val found = declared.filter { name ->
            forbidden.get().any { name.endsWith(".permission.$it") }
        }
        if (found.isNotEmpty()) {
            throw GradleException(
                "병합된 매니페스트에 금지 권한이 있습니다: ${found.joinToString()}\n" +
                    "라이브러리가 넣은 권한이면 AndroidManifest.xml에 tools:node=\"remove\"로 제거하세요.\n" +
                    "매니페스트: ${mergedManifest.get().asFile}"
            )
        }
        report.get().asFile.writeText("통과: ${declared.sorted().joinToString("\n")}\n")
    }
}

val checkForbiddenPermissions = tasks.register("checkForbiddenPermissions") {
    group = "verification"
    description = "모든 빌드 종류의 병합 매니페스트에서 금지 권한을 검사한다."
}

androidComponents {
    onVariants { variant ->
        val variantName = variant.name.replaceFirstChar { it.uppercase() }
        val checkVariant = tasks.register<CheckForbiddenPermissionsTask>("check${variantName}ForbiddenPermissions") {
            group = "verification"
            mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            forbidden.set(forbiddenPermissions)
            report.set(layout.buildDirectory.file("reports/forbiddenPermissions/${variant.name}.txt"))
        }
        checkForbiddenPermissions.configure { dependsOn(checkVariant) }
        tasks.matching { it.name == "assemble$variantName" }.configureEach { dependsOn(checkVariant) }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
