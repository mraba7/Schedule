package com.mrabah.oneuischedule.widget

internal enum class TimelineLayout {
    COMPACT, WIDE, BALANCED, TALL;
    companion object {
        fun choose(width:Float,height:Float):TimelineLayout = when {
            width>=260f && width/height>1.1f -> WIDE
            width<260f || height<220f -> COMPACT
            height/width>=1.2f && height>=350f -> TALL
            else -> BALANCED
        }
    }
}
