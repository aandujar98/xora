cmake_minimum_required(VERSION 3.22.1)
project(sora_discord)

# Built only when core/launcher/libs/discord_partner_sdk.aar is present (see build.gradle.kts).
add_library(sora_discord SHARED
    discord_bridge.cpp
)

find_package(discord_partner_sdk REQUIRED CONFIG)

target_include_directories(sora_discord PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    # Fallback if the partner AAR Prefab omits headers and the user extracted them beside the AAR.
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../libs/discord_partner_sdk/include
)

target_link_libraries(sora_discord
    discord_partner_sdk::discord_partner_sdk
    android
    log
)

target_compile_features(sora_discord PUBLIC cxx_std_20)
