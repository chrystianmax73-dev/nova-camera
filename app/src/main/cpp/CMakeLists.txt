# Inferência local com ncnn (BSD-3-Clause, Tencent) para a redução de ruído por IA (NAFNet, MIT).
# O pacote pré-compilado do ncnn é baixado pela tarefa Gradle "fetchNcnn" (versão fixa, sha256 verificado).
cmake_minimum_required(VERSION 3.22.1)
project(novadenoise)

set(ncnn_DIR ${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(novadenoise SHARED denoise.cpp)
target_link_libraries(novadenoise ncnn android jnigraphics log)


# Codec JPEG separado: upstream requer ExternalProject (não aceita add_subdirectory).
include(FetchContent)
include(ExternalProject)
FetchContent_Declare(nova_jpeg GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git
    GIT_TAG 3.1.1 GIT_SHALLOW TRUE)
FetchContent_GetProperties(nova_jpeg)
if(NOT nova_jpeg_POPULATED)
    FetchContent_Populate(nova_jpeg)
endif()
execute_process(COMMAND git rev-parse HEAD WORKING_DIRECTORY ${nova_jpeg_SOURCE_DIR}
    OUTPUT_VARIABLE NOVA_JPEG_COMMIT OUTPUT_STRIP_TRAILING_WHITESPACE RESULT_VARIABLE NOVA_JPEG_GIT_RESULT)
if(NOT NOVA_JPEG_GIT_RESULT EQUAL 0 OR NOT NOVA_JPEG_COMMIT STREQUAL "7723f50f3f66b9da74376e6d8badb6162464212c")
    message(FATAL_ERROR "Fonte libjpeg-turbo não corresponde à versão verificada")
endif()
set(NOVA_JPEG_BINARY ${CMAKE_CURRENT_BINARY_DIR}/jpeg-build)
file(MAKE_DIRECTORY ${NOVA_JPEG_BINARY})
ExternalProject_Add(nova_jpeg_build SOURCE_DIR ${nova_jpeg_SOURCE_DIR} BINARY_DIR ${NOVA_JPEG_BINARY}
    DOWNLOAD_COMMAND "" UPDATE_COMMAND "" INSTALL_COMMAND ""
    CMAKE_ARGS -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE} -DANDROID_ABI=${ANDROID_ABI}
        -DANDROID_PLATFORM=${ANDROID_PLATFORM} -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
        -DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM} -DCMAKE_POSITION_INDEPENDENT_CODE=ON
        -DCMAKE_C_VISIBILITY_PRESET=hidden -DENABLE_STATIC=ON -DENABLE_SHARED=OFF
        -DWITH_TURBOJPEG=OFF -DWITH_JAVA=OFF -DWITH_SIMD=ON
    BUILD_COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target jpeg-static --parallel 2
    BUILD_BYPRODUCTS ${NOVA_JPEG_BINARY}/libjpeg.a)
add_library(nova_jpeg_static STATIC IMPORTED)
set_target_properties(nova_jpeg_static PROPERTIES IMPORTED_LOCATION ${NOVA_JPEG_BINARY}/libjpeg.a)
add_dependencies(novadenoise nova_jpeg_build)
target_sources(novadenoise PRIVATE jpeg_bands.cpp)
target_include_directories(novadenoise PRIVATE ${nova_jpeg_SOURCE_DIR}/src ${NOVA_JPEG_BINARY})
target_link_libraries(novadenoise nova_jpeg_static)
target_link_options(novadenoise PRIVATE -Wl,--exclude-libs,ALL)
