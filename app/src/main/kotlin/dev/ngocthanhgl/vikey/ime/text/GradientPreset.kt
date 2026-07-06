package dev.ngocthanhgl.vikey.ime.text

data class GradientPreset(
    val id: String,
    val label: String,
    val colors: List<Int>,
    val angleDeg: Float,
) {
    companion object {
        val ALL = listOf(
            GradientPreset("sunset", "Sunset", listOf(0xFFFF6B35.toInt(), 0xFF6B2FA0.toInt()), 0f),
            GradientPreset("ocean", "Ocean", listOf(0xFF00B4D8.toInt(), 0xFF03045E.toInt()), 0f),
            GradientPreset("forest", "Forest", listOf(0xFF8BC34A.toInt(), 0xFF1B5E20.toInt()), 0f),
            GradientPreset("midnight", "Midnight", listOf(0xFF0D1B2A.toInt(), 0xFF000000.toInt()), 0f),
            GradientPreset("rose", "Rose", listOf(0xFFFF4081.toInt(), 0xFFB71C1C.toInt()), 0f),
            GradientPreset("aurora", "Aurora", listOf(0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt()), 30f),
            GradientPreset("warm", "Warm", listOf(0xFFFFD54F.toInt(), 0xFFFF6D00.toInt()), 90f),
            GradientPreset("cool", "Cool", listOf(0xFFB3E5FC.toInt(), 0xFFFFFFFF.toInt()), 90f),
        )
        val byId = ALL.associateBy { it.id }
    }
}
