cmake_minimum_required(VERSION 3.22)
project(plugin_bridge)

add_library(plugin_bridge SHARED plugin_bridge.cpp)
target_link_libraries(plugin_bridge android log)
