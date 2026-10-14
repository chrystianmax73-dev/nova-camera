# Inferência local com ncnn (BSD-3-Clause, Tencent) para a redução de ruído por IA (NAFNet, MIT).
# O pacote pré-compilado do ncnn é baixado pela tarefa Gradle "fetchNcnn" (versão fixa, sha256 verificado).
cmake_minimum_required(VERSION 3.22.1)
project(novadenoise)

set(ncnn_DIR ${NCNN_ROOT}/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(novadenoise SHARED denoise.cpp)
target_link_libraries(novadenoise ncnn android jnigraphics log)
