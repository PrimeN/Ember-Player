#
# Copyright 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Modified by Ember, 2026-11-16: accept external source/archive paths and support host checks.
cmake_minimum_required(VERSION 3.21.0 FATAL_ERROR)

# Enable C++20 features for the iamf_tools library.
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

# Define project name for your JNI module
project(libiamfJNI C CXX)

set(iamf_jni_root "${CMAKE_CURRENT_SOURCE_DIR}")

# Path to the compiled static iamf_tools lib.
# The build script places the static lib here.
set(IAMF_TOOLS_LIB_PATH "${IAMF_STATIC_LIBRARY}")

# Add include directory for iamf_tools.
include_directories("${IAMF_SOURCE_DIR}/iamf/include")
get_filename_component(iamf_source_name "${IAMF_SOURCE_DIR}" NAME)
include_directories("${IAMF_SOURCE_DIR}/bazel-${iamf_source_name}/external/abseil-cpp+")

# Build libiamfJNI.
add_library(iamfJNI
            SHARED
            iamf_jni.cc)

set_target_properties(iamfJNI PROPERTIES
                      CXX_VISIBILITY_PRESET hidden
                      VISIBILITY_INLINES_HIDDEN ON)

# Locate NDK log library.
if(ANDROID)
  find_library(android_log_lib log)
  target_link_libraries(iamfJNI PRIVATE android ${android_log_lib})
endif()

# Link libiamfJNI against used libraries.
target_link_libraries(iamfJNI
                      PRIVATE ${IAMF_TOOLS_LIB_PATH})
