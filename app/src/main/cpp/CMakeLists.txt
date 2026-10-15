# Inferência local com ncnn (BSD-3-Clause, Tencent) para a redução de ruído por IA (NAFNet, MIT).
# O pacote pré-compilado do ncnn é baixado pela tarefa Gradle "fetchNcnn" (versão fixa, sha256 verificado).
cmake_minimum_required(VERSION 3.22.1)
project(novadenoise)

set(ncnn_DIR ${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(novadenoise SHARED denoise.cpp)
target_link_libraries(novadenoise ncnn android jnigraphics log)


# JPEG sequencial para fotos 50/200 MP: buffers por faixa, sem bitmap de 800 MB.
include(FetchContent)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG OFF CACHE BOOL "" FORCE)
set(WITH_JAVA OFF CACHE BOOL "" FORCE)
FetchContent_Declare(nova_jpeg GIT_REPOSITORY https://github.com/libjpeg-turbo/libjpeg-turbo.git
    GIT_TAG 7723f50f3f66b9da74376e6d8badb6162464212c)
FetchContent_MakeAvailable(nova_jpeg)
target_sources(novadenoise PRIVATE jpeg_bands.cpp)
target_include_directories(novadenoise PRIVATE ${nova_jpeg_SOURCE_DIR}/src ${nova_jpeg_BINARY_DIR})
target_link_libraries(novadenoise jpeg-static)
target_link_options(novadenoise PRIVATE -Wl,--exclude-libs,ALL)
