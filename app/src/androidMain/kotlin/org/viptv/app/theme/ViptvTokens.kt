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
    val surfaceN1Pressed = Color(0xFF1C1C1F)
    val surfaceAvatar = Color(0xFF0E0E10)
    val textPrimary = Color(0xFFF4F2EE)
    val textBody = Color(0xFFDAD8D3)
    val textSecondary = Color(0xFFB6B4AF)
    val textTertiary = Color(0xFF8F8D89)
    val textOnLightSecondary = Color(0xFF4A4945)
    val textOnLightAccent = Color(0xFF6B5A12)
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
    val statusDangerOnLight = Color(0xFFB42318)
    val statusSpinnerOnAccent = Color(0x40000000)
    val lineHairline = Color(0x12FFFFFF)
    val lineChip = Color(0x1AFFFFFF)
    val lineOutline = Color(0x24FFFFFF)
    val lineStrong = Color(0x38FFFFFF)
    val lineControl = Color(0x4DFFFFFF)
    val lineLinkHover = Color(0xCCFFFFFF)
    val lineKeycapTv = Color(0x47FFFFFF)
    val lineOnAccent = Color(0x2E000000)
    val lineSelectedTv = Color(0x80FFFFFF)
    val fillTvUnfocused = Color(0x1FFFFFFF)
    val fillTvSelected = Color(0x29FFFFFF)
    val fillGlass = Color(0xF0202023)
    val fillBadgeGlass = Color(0x8C000000)
    val fillWhite = Color(0xFFFFFFFF)
    val fillLightPressed = Color(0xFFDDDBD6)
    val fillWash = Color(0x0FFFFFFF)
    val fillWashPressed = Color(0x0AFFFFFF)
    val fillTag = Color(0x1AFFFFFF)
    val fillTvField = Color(0x12FFFFFF)
    val fillBuffered = Color(0x66FFFFFF)
    val fillScrollbar = Color(0x59FFFFFF)
    val fillOnAccentHover = Color(0x14000000)
    val fillOnLightBadge = Color(0x1A000000)
    val fillLiveGlass = Color(0x80000000)
    val fillWatchingBadge = Color(0xA6000000)
    val fillNoticeGlass = Color(0xE6161618)
    val fillNoticeGlassTv = Color(0xEB161618)
    val fillPopoverGlass = Color(0xF7161618)
    val fillDot = Color(0xFF3A3A3F)
    val fillDotOverArt = Color(0x73FFFFFF)
    val fillTvSwitchOff = Color(0x33FFFFFF)
    val scrimSheet = Color(0x9E000000)
    val scrimDialog = Color(0x8C000000)
    val scrimTvPanel = Color(0x99000000)
    val scrimTvMenu = Color(0x8C000000)
    val scrimTvFullscreen = Color(0xF00B0B0C)
    val scrimCardHover = Color(0x61000000)
    val scrimCardPressed = Color(0x40000000)
    val skeletonGround = Color(0xFF17171A)
    val skeletonCard = Color(0xFF1E1E21)
    val skeletonShimmer = Color(0x0AFFFFFF)
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
    val radiusN2xs = 6.dp
    val radiusSmPlus = 10.dp
    val radiusXlPlus = 20.dp
    val radiusN2xlPlus = 24.dp
    val radiusSheetTop = 30.dp
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
    val layoutPhoneSheetTop = 150.dp
    val layoutPhoneSheetBottom = 34.dp
    val layoutDesktopTitlebar = 40.dp
    val layoutDesktopRail = 84.dp
    val layoutDesktopDialogWidth = 460.dp
    val layoutDesktopDrawerWidth = 460.dp
    val layoutDesktopPopupWidth = 340.dp
    val layoutDesktopPopoverWidth = 260.dp
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
    val sizeButtonPill = 44.dp
    val sizeButtonSmall = 40.dp
    val sizeButtonSplitChevron = 44.dp
    val sizeChipPhone = 44.dp
    val sizeChipDesktop = 40.dp
    val sizeChipDesktopDrawer = 36.dp
    val sizeChipTv = 56.dp
    val sizeFieldPhone = 54.dp
    val sizeFieldDesktop = 48.dp
    val sizeFieldDesktopTitlebar = 30.dp
    val sizeFieldTv = 80.dp
    val sizeFieldTvEntry = 96.dp
    val sizeFieldSearchPhone = 52.dp
    val sizeFieldSearchTitlebarWidth = 460.dp
    val sizeFieldRadiusPhone = 16.dp
    val sizeFieldRadiusDesktop = 12.dp
    val sizeFieldRadiusTv = 20.dp
    val sizeRowTv = 80.dp
    val sizeRowSettings = 60.dp
    val sizeRowChoicePhone = 56.dp
    val sizeRowChoiceDesktop = 48.dp
    val sizeRowMenu = 40.dp
    val sizeRowSectionNav = 44.dp
    val sizeRowSettingsIcon = 34.dp
    val sizeRowSettingsIconRadius = 11.dp
    val sizeProgressPhone = 4.dp
    val sizeProgressDesktop = 4.dp
    val sizeProgressTv = 6.dp
    val sizeProgressSmall = 3.dp
    val sizeIconPhone = 20.dp
    val sizeIconPhoneRound = 22.dp
    val sizeIconDesktop = 18.dp
    val sizeIconDesktopRound = 20.dp
    val sizeIconTv = 28.dp
    val sizeIconTvRound = 30.dp
    val sizeIconSmall = 16.dp
    val sizeIconAlert = 15.dp
    val sizeSwitchWidth = 52.dp
    val sizeSwitchHeight = 32.dp
    val sizeSwitchKnob = 26.dp
    val sizeSwitchTvWidth = 72.dp
    val sizeSwitchTvHeight = 42.dp
    val sizeSwitchTvKnob = 36.dp
    val sizeSwitchInset = 3.dp
    val sizeRadio = 20.dp
    val sizeSpinnerDefault = 12.dp
    val sizeSpinnerInline = 14.dp
    val sizeSpinnerTv = 18.dp
    val sizeSpinnerBuffering = 48.dp
    val sizeSpinnerTvPanel = 56.dp
    val sizeSpinnerStroke = 2.dp
    val sizeSpinnerStrokeTv = 3.dp
    val sizeSpinnerStrokeTvPanel = 5.dp
    val sizeLiveDotDefault = 8.dp
    val sizeLiveDotTv = 12.dp
    val sizeLiveDotBadge = 6.dp
    val sizeDotDefault = 6.dp
    val sizeDotActiveWidth = 18.dp
    val sizeGrabberWidth = 40.dp
    val sizeGrabberHeight = 5.dp
    val sizeGrabberHit = 28.dp
    val sizeCloseDiscDefault = 36.dp
    val sizeCloseDiscSmall = 32.dp
    val sizeCloseDiscTitlebar = 20.dp
    val sizeToastPhoneBottom = 116.dp
    val sizeToastDesktopBottom = 24.dp
    val sizeToastTvTop = 54.dp
    val sizeToastPhoneMax = 358.dp
    val sizeToastDesktopMax = 560.dp
    val sizeToastHeight = 44.dp
    val sizeToastNoticeHeight = 40.dp
    val sizeToastActionHeight = 32.dp
    val sizeToastTvHeight = 84.dp
    val sizeToastTvNoticeHeight = 64.dp
    val sizeToastTvActionHeight = 60.dp
    val sizeKeycapHeight = 20.dp
    val sizeKeycapRadius = 6.dp
    val sizeKeycapTvHeight = 34.dp
    val sizeKeycapTvMinWidth = 40.dp
    val sizeKeycapTvRadius = 10.dp
    val sizeKeycapTvBorder = 2.dp
    val sizePinWidth = 44.dp
    val sizePinHeight = 52.dp
    val sizePinDot = 12.dp
    val sizePinCaret = 22.dp
    val sizePinTvWidth = 72.dp
    val sizePinTvHeight = 80.dp
    val sizePinTvDot = 18.dp
    val sizePinTvCaret = 36.dp
    val sizeCaretWidth = 2.dp
    val sizeCaretHeight = 20.dp
    val sizeCaretTvWidth = 3.dp
    val sizeKeyTv = 64.dp
    val sizeKeyTvRadius = 14.dp
    val sizeKeyTvKeypad = 80.dp
    val sizeKeyTvKeyboardWidth = 640.dp
    val sizeKeyTvKeypadWidth = 420.dp
    val sizeEmptyIconPhone = 52.dp
    val sizeEmptyIconDesktop = 60.dp
    val sizeEmptyIconTv = 88.dp
    val sizeBannerIconDefault = 40.dp
    val sizeBannerIconTv = 72.dp
    val sizeTilePhonePosterWidth = 111.dp
    val sizeTilePhonePosterHeight = 139.dp
    val sizeTilePhoneContinueWidth = 292.dp
    val sizeTilePhoneContinueHeight = 96.dp
    val sizeTilePhoneContinueThumbWidth = 60.dp
    val sizeTilePhoneContinueThumbHeight = 76.dp
    val sizeTilePhoneLiveNowWidth = 200.dp
    val sizeTilePhoneChannelLogoWidth = 60.dp
    val sizeTilePhoneChannelLogoHeight = 60.dp
    val sizeTilePhoneProfileWidth = 112.dp
    val sizeTilePhoneProfileHeight = 112.dp
    val sizeTilePhoneAvatarWidth = 96.dp
    val sizeTilePhoneAvatarHeight = 96.dp
    val sizeTilePhoneSourceQualityWidth = 56.dp
    val sizeTilePhoneSourceQualityHeight = 44.dp
    val sizeTileDesktopPosterWidth = 172.dp
    val sizeTileDesktopPosterHeight = 258.dp
    val sizeTileWebPosterWidth = 164.dp
    val sizeTileWebPosterHeight = 246.dp
    val sizeTileDesktopContinueWidth = 256.dp
    val sizeTileDesktopContinueHeight = 128.dp
    val sizeTileDesktopEpisodeWidth = 272.dp
    val sizeTileDesktopEpisodeHeight = 150.dp
    val sizeTileDesktopLiveWidth = 220.dp
    val sizeTileDesktopLiveHeight = 124.dp
    val sizeTileDesktopProfileWidth = 140.dp
    val sizeTileDesktopProfileHeight = 140.dp
    val sizeTileDesktopAvatarWidth = 120.dp
    val sizeTileDesktopAvatarHeight = 120.dp
    val sizeTileDesktopSourceQualityWidth = 54.dp
    val sizeTileDesktopSourceQualityHeight = 40.dp
    val sizeTileTvStillWidth = 320.dp
    val sizeTileTvStillHeight = 180.dp
    val sizeTileTvGridWidth = 360.dp
    val sizeTileTvGridHeight = 202.dp
    val sizeTileTvEpisodeWidth = 360.dp
    val sizeTileTvEpisodeHeight = 200.dp
    val sizeTileTvProfileWidth = 220.dp
    val sizeTileTvProfileHeight = 220.dp
    val sizeTileTvAvatarWidth = 170.dp
    val sizeTileTvAvatarHeight = 170.dp
    val sizeTileTvSourceQualityWidth = 92.dp
    val sizeTileTvSourceQualityHeight = 56.dp
    val sizeTileLockBadge = 30.dp
    val sizeTileLockBadgeDesktop = 32.dp
    val sizeTileLockBadgeTv = 48.dp
    val sizeTileCheckBadge = 24.dp
    val sizeTileCheckBadgeTv = 34.dp
    val sizeTilePlayDisc = 44.dp
    val sizeTileHoverPlayDisc = 48.dp
    val sizeTileSelectedRing = 3.dp
    val sizeSourceRowPhone = 76.dp
    val sizeSourceRowDesktop = 68.dp
    val sizeSourceRowTv = 104.dp
    val sizeDividerPhone = 24.dp
    val sizeDividerDesktop = 28.dp
    val sizeDividerTv = 32.dp
    val sizeBorderFocus = 1.5.dp
    val sizeBorderStrong = 2.dp
    val sizeBorderTvStrong = 3.dp
    val sizeBlurGlass = 20.dp
    val sizeBlurNotice = 16.dp
    val sizeBlurBadge = 12.dp
    val sizeTimelineHeight = 20.dp
    val sizeTimelineKnob = 14.dp
    val sizeTimelineMarker = 12.dp
    val sizeTimelineBubble = 26.dp
    val sizeTimelineTvHeight = 32.dp
    val sizeTimelineTvTrackFocus = 10.dp
    val sizeTimelineTvKnob = 32.dp
    val sizeTimelineTvBubble = 56.dp
    val focusTvCaptionShift = 8.dp
    val focusTvCaptionShiftProfile = 10.dp
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
    val phoneButtonDetail = ViptvTypeRole(display = false, size = 18f, weight = FontWeight(700), lineHeight = 1f, letterSpacingEm = 0f)
    val phoneEmptyTitle = ViptvTypeRole(display = true, size = 22f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = -0.01f)
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
    val desktopEmptyTitle = ViptvTypeRole(display = true, size = 26f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = -0.01f)
    val desktopMonogram = ViptvTypeRole(display = true, size = 30f, weight = FontWeight(800), lineHeight = 1f, letterSpacingEm = -0.02f)
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
    val tvEmptyTitle = ViptvTypeRole(display = true, size = 40f, weight = FontWeight(700), lineHeight = 1.15f, letterSpacingEm = -0.01f)
    val tvField = ViptvTypeRole(display = false, size = 30f, weight = FontWeight(400), lineHeight = 1.2f, letterSpacingEm = 0f)
    val tvFieldLarge = ViptvTypeRole(display = false, size = 34f, weight = FontWeight(600), lineHeight = 1.2f, letterSpacingEm = 0f)
    val tvEntry = ViptvTypeRole(display = false, size = 38f, weight = FontWeight(600), lineHeight = 1.2f, letterSpacingEm = 0f)
    val tvStatus = ViptvTypeRole(display = false, size = 20f, weight = FontWeight(700), lineHeight = 1.2f, letterSpacingEm = 0.1f)
}

object ViptvMotion {
    const val focus = 150L
    const val pressedScale = 0.97f
    const val toastNotice = 5000L
    const val toastError = 4000L
    const val playerNotice = 4000L
    const val spinner = 800L
    const val shimmer = 1400L
    const val pressedScaleRow = 0.99f
    const val pressedScaleCard = 0.98f
}
