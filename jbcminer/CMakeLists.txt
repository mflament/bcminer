cmake_minimum_required(VERSION 3.9)
set(CMAKE_GENERATOR_PLATFORM "x64")

project(cppminer)
add_library(cppminer SHARED src/main/native/cppminer.cpp)
# set_target_properties(cppminer PROPERTIES SUFFIX "$ENV{CPPMINER_LIB_SUFFIX}")

find_package(JNI)
if (!JNI_FOUND)
    message (FATAL_ERROR "JNI not found")
endif()

include_directories("target/generated-sources/native/include" ${JNI_INCLUDE_DIRS})
