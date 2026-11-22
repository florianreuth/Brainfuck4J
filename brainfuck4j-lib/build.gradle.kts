plugins {
    id("publishing.florianreuth")
    id("base.junit")
}

configureSourceSet("example")

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
