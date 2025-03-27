package com.wardtn.uvccamera.render.env


/**
 * 旋转角度
 */
enum class RotateType {
    ANGLE_0,        // default, do nothing
    ANGLE_90,
    ANGLE_180,
    ANGLE_270,
    FLIP_UP_DOWN,    // flip vertically
    FLIP_LEFT_RIGHT  // horizontal flip(mirror)
}