plugins {
    id("org.jetbrains.kotlin.jvm")
}

dependencies {
    implementation("org.msgpack:msgpack-core:0.9.12")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(17)
}
