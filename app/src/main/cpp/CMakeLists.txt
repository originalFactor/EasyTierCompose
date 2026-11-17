cmake_minimum_required(VERSION 3.22.1)
project(easytier_root_tun)


set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
add_library(root_tun_jni SHARED root_tun_jni.cpp)
find_library(log-lib log)
target_link_libraries(root_tun_jni ${log-lib})

include(CheckIPOSupported)
check_ipo_supported(RESULT root_tun_ipo_supported OUTPUT root_tun_ipo_error LANGUAGES CXX)
if(NOT root_tun_ipo_supported)
    message(FATAL_ERROR "Root TUN LTO is unavailable: ${root_tun_ipo_error}")
endif()
# AGP uses RelWithDebInfo for release builds and strips symbols when packaging.
set_property(TARGET root_tun_jni PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELEASE TRUE)
set_property(TARGET root_tun_jni PROPERTY INTERPROCEDURAL_OPTIMIZATION_RELWITHDEBINFO TRUE)
target_compile_options(root_tun_jni PRIVATE
    $<$<CONFIG:Release,RelWithDebInfo>:-O3>
    $<$<CONFIG:Release,RelWithDebInfo>:-ffunction-sections>
    $<$<CONFIG:Release,RelWithDebInfo>:-fdata-sections>
    $<$<CONFIG:Release,RelWithDebInfo>:-fvisibility=hidden>
    $<$<CONFIG:Release,RelWithDebInfo>:-fvisibility-inlines-hidden>
)
target_link_options(root_tun_jni PRIVATE
    $<$<CONFIG:Release,RelWithDebInfo>:-Wl,--gc-sections>
    $<$<CONFIG:Release,RelWithDebInfo>:-Wl,--icf=safe>
)
