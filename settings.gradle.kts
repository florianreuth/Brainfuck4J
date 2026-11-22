pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("base.settings")
}

rootProject.name = "brainfuck4j"

include("brainfuck4j-lib")
include("brainfuck4j-cli")
include("brainfuck4j-app")
