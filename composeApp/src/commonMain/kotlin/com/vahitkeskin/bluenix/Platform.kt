package com.vahitkeskin.bluenix

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform