package com.stackdrop.game.game

/** Axis-aligned box in world units: x/z are the min corner on the ground plane, y is the bottom. */
class Block(
    var x: Float,
    var z: Float,
    var w: Float,
    var d: Float,
    var y: Float,
    var h: Float,
    var color: Int,
)
