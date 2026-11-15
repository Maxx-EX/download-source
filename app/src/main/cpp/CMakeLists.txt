cmake_minimum_required(VERSION 3.22)
project(zerocore LANGUAGES CXX)

# 开启 C++17 支持并优化编译速度
add_library(zerocore SHARED zerocore.cpp)
target_compile_features(zerocore PRIVATE cxx_std_17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto")

# 链接 Android 原生日志库 (log) 和 Zlib 校验库 (z)
find_library(log-lib log)
find_library(z-lib z)

# 确保库被正确链接
target_link_libraries(zerocore ${log-lib} ${z-lib})
