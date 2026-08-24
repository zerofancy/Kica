package top.ntutn.kica.ui.state




enum class WindowLayout {
    PHONE,
    TABLET,
    DESKTOP,
}

fun classifyWindow(widthDp: Int): WindowLayout = when {
    widthDp < 600 -> WindowLayout.PHONE
    widthDp < 840 -> WindowLayout.TABLET
    else -> WindowLayout.DESKTOP
}
