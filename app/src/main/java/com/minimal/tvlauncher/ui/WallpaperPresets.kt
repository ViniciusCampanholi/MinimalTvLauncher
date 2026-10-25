package com.minimal.tvlauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

enum class WallpaperPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val previewColors: List<Color>
) {
    PURE_BLACK(
        id = "pure_black",
        title = "Preto Puro (OLED)",
        subtitle = "Fundo 100% preto sem iluminação de pixels",
        previewColors = listOf(Color(0xFF000000), Color(0xFF050505))
    ),
    MIDNIGHT_BLUE(
        id = "midnight_blue",
        title = "Azul Meia-Noite",
        subtitle = "Degradê profundo em tons de azul abissal",
        previewColors = listOf(Color(0xFF0A192F), Color(0xFF040B16), Color(0xFF000000))
    ),
    DARK_AURORA(
        id = "dark_aurora",
        title = "Aurora Noturna",
        subtitle = "Nuances suaves de verde-esmeralda, ciano e preto",
        previewColors = listOf(Color(0xFF062C2D), Color(0xFF0A1828), Color(0xFF020408))
    ),
    STARFIELD_NIGHT(
        id = "starfield_night",
        title = "Céu Estrelado",
        subtitle = "Noite escura com pontos estelares minimalistas",
        previewColors = listOf(Color(0xFF090D16), Color(0xFF030508), Color(0xFF000000))
    ),
    CARBON_GRAPHITE(
        id = "carbon_graphite",
        title = "Grafite Carbono",
        subtitle = "Cinza grafite fosco de baixo contraste",
        previewColors = listOf(Color(0xFF1C1D21), Color(0xFF101114), Color(0xFF070709))
    ),
    CYBER_DUSK(
        id = "cyber_dusk",
        title = "Crepúsculo Violeta",
        subtitle = "Tons escuros de violeta e púrpura no horizonte",
        previewColors = listOf(Color(0xFF1F0C33), Color(0xFF2B0E24), Color(0xFF050308))
    ),
    DEEP_EMERALD(
        id = "deep_emerald",
        title = "Floresta Noturna",
        subtitle = "Verde-musgo escuro e aconchegante",
        previewColors = listOf(Color(0xFF072218), Color(0xFF03100B), Color(0xFF000000))
    ),
    DARK_CRIMSON(
        id = "dark_crimson",
        title = "Vinho Escuro",
        subtitle = "Tom borgonha suave ideal para ambientes escuros",
        previewColors = listOf(Color(0xFF280911), Color(0xFF120408), Color(0xFF000000))
    );

    companion object {
        fun fromId(id: String?): WallpaperPreset {
            return entries.firstOrNull { it.id == id } ?: PURE_BLACK
        }
    }
}

@Composable
fun WallpaperBackground(
    preset: WallpaperPreset,
    modifier: Modifier = Modifier
) {
    when (preset) {
        WallpaperPreset.PURE_BLACK -> {
            Box(modifier = modifier.background(Color.Black))
        }
        WallpaperPreset.STARFIELD_NIGHT -> {
            Canvas(
                modifier = modifier.background(
                    Brush.verticalGradient(preset.previewColors)
                )
            ) {
                // Desenha brilho nebuloso sutil e estrelas fixas determinísticas
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF162447).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(size.width * 0.75f, size.height * 0.25f),
                        radius = size.minDimension * 0.6f
                    ),
                    center = Offset(size.width * 0.75f, size.height * 0.25f),
                    radius = size.minDimension * 0.6f
                )

                val starPositions = listOf(
                    Offset(0.08f, 0.14f) to 1.6f,
                    Offset(0.15f, 0.32f) to 1.2f,
                    Offset(0.22f, 0.11f) to 2.0f,
                    Offset(0.28f, 0.68f) to 1.4f,
                    Offset(0.35f, 0.24f) to 1.8f,
                    Offset(0.42f, 0.82f) to 1.2f,
                    Offset(0.49f, 0.16f) to 2.2f,
                    Offset(0.56f, 0.45f) to 1.5f,
                    Offset(0.63f, 0.19f) to 1.7f,
                    Offset(0.71f, 0.74f) to 1.3f,
                    Offset(0.78f, 0.12f) to 2.1f,
                    Offset(0.84f, 0.38f) to 1.6f,
                    Offset(0.91f, 0.22f) to 1.9f,
                    Offset(0.94f, 0.64f) to 1.4f,
                    Offset(0.12f, 0.78f) to 1.8f,
                    Offset(0.67f, 0.31f) to 1.2f,
                    Offset(0.39f, 0.52f) to 1.4f,
                    Offset(0.82f, 0.86f) to 1.7f
                )

                for ((relPos, radiusPx) in starPositions) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.45f),
                        radius = radiusPx,
                        center = Offset(size.width * relPos.x, size.height * relPos.y)
                    )
                }
            }
        }
        WallpaperPreset.DARK_AURORA -> {
            Canvas(
                modifier = modifier.background(Color(0xFF020408))
            ) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = preset.previewColors,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height)
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF0D5C56).copy(alpha = 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.2f),
                        radius = size.maxDimension * 0.55f
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.2f),
                    radius = size.maxDimension * 0.55f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF12355B).copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.8f, size.height * 0.75f),
                        radius = size.maxDimension * 0.5f
                    ),
                    center = Offset(size.width * 0.8f, size.height * 0.75f),
                    radius = size.maxDimension * 0.5f
                )
            }
        }
        else -> {
            Box(
                modifier = modifier.background(
                    Brush.linearGradient(
                        colors = preset.previewColors,
                        start = Offset(0f, 0f),
                        end = Offset(1600f, 1000f)
                    )
                )
            )
        }
    }
}
