// GENERATED FILE — DO NOT EDIT.
// Source: design/viptv-design-system/tokens/tokens.json
// Regenerate: node design/viptv-design-system/tools/gen-themes.mjs
package org.viptv.app.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontWeight

/** VIPTV design tokens. The accent is a runtime value: read it from settings, default [ViptvColor.AccentDefault]. */
object ViptvColor {
    val bg = Color(0xFF0B0B0C)
    val bgOled = Color(0xFF000000)
    val surfaceN1 = Color(0xFF161618)
    val surfaceN2 = Color(0xFF212124)
    val surfaceN3 = Color(0xFF2A2A2E)
    val surfaceN4 = Color(0xFF34343A)
    val textPrimary = Color(0xFFF4F2EE)
    val textBody = Color(0xFFDAD8D3)
    val textSecondary = Color(0xFFB6B4AF)
    val textTertiary = Color(0xFF8F8D89)
    val onLight = Color(0xFF111113)
    val onAccent = Color(0xFF15130F)
    val accentDefault = Color(0xFFF5C542)
    val accentOptionsGold = Color(0xFFF5C542)
    val accentOptionsCoral = Color(0xFFFF8B5C)
    val accentOptionsMint = Color(0xFF62D9BC)
    val accentOptionsPeriwinkle = Color(0xFFA3BCFF)
    val statusLive = Color(0xFFFF5A4E)
    val statusDanger = Color(0xFFFF7A6E)
    val statusDangerTv = Color(0xFFFF8A7E)
    val statusSpinnerTrack = Color(0xFF45454B)
    val lineHairline = Color(0x12FFFFFF)
    val lineChip = Color(0x1AFFFFFF)
    val lineOutline = Color(0x24FFFFFF)
    val lineStrong = Color(0x38FFFFFF)
    val fillTvUnfocused = Color(0x1FFFFFFF)
    val fillTvSelected = Color(0x29FFFFFF)
    val fillGlass = Color(0xF0202023)
    val fillBadgeGlass = Color(0x8C000000)
    val scrimSheet = Color(0x9E000000)
    val scrimDialog = Color(0x8C000000)
    val scrimTvPanel = Color(0x99000000)
    val scrimTvMenu = Color(0x8C000000)
    val scrimTvFullscreen = Color(0xF00B0B0C)
}

object ViptvDimen {
    val radiusXs = 4.dp
    val radiusSm = 8.dp
    val radiusFieldTitlebar = 9.dp
    val radiusMd = 12.dp
    val radiusLg = 14.dp
    val radiusXl = 16.dp
    val radiusN2xl = 18.dp
    val radiusN3xl = 22.dp
    val radiusSheet = 28.dp
    val radiusPhoneFrame = 44.dp
    val spaceN0 = 0.dp
    val spaceN1 = 4.dp
    val spaceN2 = 8.dp
    val spaceN3 = 12.dp
    val spaceN4 = 16.dp
    val spaceN5 = 20.dp
    val spaceN6 = 24.dp
    val spaceN7 = 28.dp
    val spaceN8 = 32.dp
    val spaceN9 = 36.dp
    val spaceN12 = 48.dp
    val spaceN16 = 64.dp
    val spaceN24 = 96.dp
    val spaceN05 = 2.dp
    val spaceN15 = 6.dp
    val spaceN25 = 10.dp
    val spaceN35 = 14.dp
    val spaceN45 = 18.dp
    val spaceN55 = 22.dp
    val layoutPhoneGutter = 16.dp
    val layoutPhoneTopInset = 58.dp
    val layoutPhoneNavHeight = 64.dp
    val layoutPhoneNavBottom = 28.dp
    val layoutPhoneMinTarget = 44.dp
    val layoutPhoneBottomClearance = 140.dp
    val layoutDesktopTitlebar = 40.dp
    val layoutDesktopRail = 84.dp
    val layoutDesktopDialogWidth = 460.dp
    val layoutDesktopDrawerWidth = 460.dp
    val layoutDesktopPopupWidth = 340.dp
    val layoutTvSafeX = 96.dp
    val layoutTvSafeY = 54.dp
    val layoutTvRailCollapsed = 144.dp
    val layoutTvMenuExpanded = 520.dp
    val layoutTvPanelWidth = 820.dp
    val layoutTvRowGap = 36.dp
    val layoutTvHeadingToRow = 22.dp
    val layoutTvArtToCaption = 16.dp
    val sizeButtonPhone = 54.dp
    val sizeButtonPhoneDetail = 58.dp
    val sizeButtonDesktop = 48.dp
    val sizeButtonTv = 72.dp
    val sizeButtonTvSmall = 52.dp
    val sizeChipPhone = 44.dp
    val sizeChipDesktop = 40.dp
    val sizeChipDesktopDrawer = 36.dp
    val sizeChipTv = 56.dp
    val sizeFieldPhone = 54.dp
    val sizeFieldDesktop = 48.dp
    val sizeFieldDesktopTitlebar = 30.dp
    val sizeFieldTv = 80.dp
    val sizeFieldRadiusPhone = 16.dp
    val sizeFieldRadiusDesktop = 12.dp
    val sizeFieldRadiusTv = 20.dp
    val sizeRowTv = 80.dp
    val sizeProgressPhone = 4.dp
    val sizeProgressDesktop = 4.dp
    val sizeProgressTv = 6.dp
}

data class ViptvTypeRole(val display: Boolean, val size: Float, val weight: FontWeight, val lineHeight: Float, val letterSpacingEm: Float)

object ViptvType {
    val phoneScreenTitle = ViptvTypeRole(display = true, size = 34f, weight = FontWeight(700), lineHeight = 1.05f, letterSpacingEm = -0.02f)
    val phoneSheetTitle = ViptvTypeRole(display = true, size = 24f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = -0.01f)
    val phoneWordmark = ViptvTypeRole(display = true, size = 24f, weight = FontWeight(800), lineHeight = 1f, letterSpacingEm = -0.03f)
    val phoneSection = ViptvTypeRole(display = true, size = 20f, weight = FontWeight(650), lineHeight = 1.15f, letterSpacingEm = -0.01f)
    val phoneButtonPrimary = ViptvTypeRole(display = false, size = 17f, weight = FontWeight(700), lineHeight = 1f, letterSpacingEm = 0f)
    val phoneBodyStrong = ViptvTypeRole(display = false, size = 16f, weight = FontWeight(600), lineHeight = 1.35f, letterSpacingEm = 0f)
    val phoneBody = ViptvTypeRole(display = false, size = 15f, weight = FontWeight(400), lineHeight = 1.45f, letterSpacingEm = 0f)
    val phoneLabel = ViptvTypeRole(display = false, size = 14f, weight = FontWeight(600), lineHeight = 1.3f, letterSpacingEm = 0f)
    val phoneMeta = ViptvTypeRole(display = false, size = 13f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val phoneCaption = ViptvTypeRole(display = false, size = 12f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val phoneEyebrow = ViptvTypeRole(display = false, size = 11f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = 0.08f)
    val phoneNavLabel = ViptvTypeRole(display = false, size = 11f, weight = FontWeight(600), lineHeight = 1f, letterSpacingEm = 0.01f)
    val desktopPageTitle = ViptvTypeRole(display = true, size = 40f, weight = FontWeight(700), lineHeight = 1.05f, letterSpacingEm = -0.02f)
    val desktopHeroTitle = ViptvTypeRole(display = true, size = 30f, weight = FontWeight(800), lineHeight = 1.05f, letterSpacingEm = -0.02f)
    val desktopDialogTitle = ViptvTypeRole(display = true, size = 24f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = -0.01f)
    val desktopSection = ViptvTypeRole(display = true, size = 22f, weight = FontWeight(650), lineHeight = 1.15f, letterSpacingEm = -0.01f)
    val desktopWordmark = ViptvTypeRole(display = true, size = 13f, weight = FontWeight(700), lineHeight = 1f, letterSpacingEm = 0.02f)
    val desktopButton = ViptvTypeRole(display = false, size = 16f, weight = FontWeight(600), lineHeight = 1f, letterSpacingEm = 0f)
    val desktopBody = ViptvTypeRole(display = false, size = 16f, weight = FontWeight(400), lineHeight = 1.5f, letterSpacingEm = 0f)
    val desktopLabel = ViptvTypeRole(display = false, size = 15f, weight = FontWeight(600), lineHeight = 1.3f, letterSpacingEm = 0f)
    val desktopRow = ViptvTypeRole(display = false, size = 14f, weight = FontWeight(600), lineHeight = 1.3f, letterSpacingEm = 0f)
    val desktopMeta = ViptvTypeRole(display = false, size = 13f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val desktopCaption = ViptvTypeRole(display = false, size = 12f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val desktopEyebrow = ViptvTypeRole(display = false, size = 12f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = 0.06f)
    val desktopRailLabel = ViptvTypeRole(display = false, size = 11f, weight = FontWeight(600), lineHeight = 1f, letterSpacingEm = 0f)
    val tvScreenTitle = ViptvTypeRole(display = true, size = 56f, weight = FontWeight(700), lineHeight = 1.05f, letterSpacingEm = -0.02f)
    val tvPanelTitle = ViptvTypeRole(display = true, size = 44f, weight = FontWeight(700), lineHeight = 1.1f, letterSpacingEm = -0.01f)
    val tvSection = ViptvTypeRole(display = true, size = 32f, weight = FontWeight(650), lineHeight = 1.1f, letterSpacingEm = -0.01f)
    val tvWordmark = ViptvTypeRole(display = true, size = 40f, weight = FontWeight(800), lineHeight = 1f, letterSpacingEm = -0.02f)
    val tvRow = ViptvTypeRole(display = false, size = 28f, weight = FontWeight(600), lineHeight = 1.3f, letterSpacingEm = 0f)
    val tvButton = ViptvTypeRole(display = false, size = 26f, weight = FontWeight(700), lineHeight = 1f, letterSpacingEm = 0f)
    val tvBody = ViptvTypeRole(display = false, size = 26f, weight = FontWeight(400), lineHeight = 1.45f, letterSpacingEm = 0f)
    val tvLabel = ViptvTypeRole(display = false, size = 24f, weight = FontWeight(600), lineHeight = 1.3f, letterSpacingEm = 0f)
    val tvMeta = ViptvTypeRole(display = false, size = 22f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val tvCaption = ViptvTypeRole(display = false, size = 20f, weight = FontWeight(400), lineHeight = 1.35f, letterSpacingEm = 0f)
    val tvEyebrow = ViptvTypeRole(display = false, size = 18f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = 0.08f)
    val tvMin = ViptvTypeRole(display = false, size = 18f, weight = FontWeight(400), lineHeight = 1.3f, letterSpacingEm = 0f)
}

object ViptvMotion {
    const val pressedScale = 0.97f
    const val toastNotice = 5000L
    const val toastError = 4000L
    const val playerNotice = 4000L
}
