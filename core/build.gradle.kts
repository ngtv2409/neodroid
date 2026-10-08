plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.msgpack:msgpack-core:0.9.12")
}

kotlin {
    jvmToolchain(17)
}
