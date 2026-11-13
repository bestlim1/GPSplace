// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9 내장 Kotlin의 기본 KGP(2.2.10)보다 높은 버전을 쓴다.
        // Firebase BoM·maps-compose 최신판이 Kotlin 2.4 메타데이터로 컴파일되어 있어 2.2 컴파일러로는 읽지 못한다.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
}
