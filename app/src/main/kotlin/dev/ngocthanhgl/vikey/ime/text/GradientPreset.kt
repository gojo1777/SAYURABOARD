package dev.ngocthanhgl.vikey.ime.text

data class GradientPreset(
    val id: String,
    val label: String,
    val colors: List<Int>,
    val angleDeg: Float,
) {
    companion object {
        val ALL = listOf(
            GradientPreset("sunset", "Sunset", listOf(0xFFFF6B35, 0xFF6B2FA0), 0f),
            GradientPreset("ocean", "Ocean", listOf(0xFF00B4D8, 0xFF03045E), 0f),
            GradientPreset("forest", "Forest", listOf(0xFF8BC34A, 0xFF1B5E20), 0f),
            GradientPreset("midnight", "Midnight", listOf(0xFF0D1B2A, 0xFF000000), 0f),
            GradientPreset("rose", "Rose", listOf(0xFFFF4081, 0xFFB71C1C), 0f),
            GradientPreset("aurora", "Aurora", listOf(0xFF7C4DFF, 0xFF00E5FF), 30f),
            GradientPreset("warm", "Warm", listOf(0xFFFFD54F, 0xFFFF6D00), 90f),
            GradientPreset("cool", "Cool", listOf(0xFFB3E5FC, 0xFFFFFFFF), 90f),
        )
        val byId = ALL.associateBy { it.id }
    }
}
