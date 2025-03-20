cmake_minimum_required(VERSION 3.4.1)

add_library(native-lib SHARED cpp/native-lib.cpp)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(native-lib android ${log-lib} ${jnigraphics-lib})