cmake_minimum_required(VERSION 3.10.2)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(${CMAKE_SOURCE_DIR}/cpp/include)
set(ONNX_RUNTIME_DIR ${CMAKE_SOURCE_DIR}/jniLibs/${ANDROID_ABI})
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION ${ONNX_RUNTIME_DIR}/libonnxruntime.so)

add_library(native-lib SHARED cpp/native-lib.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(native-lib android ${log-lib} ${jnigraphics-lib})
target_link_libraries(native-lib onnxruntime)