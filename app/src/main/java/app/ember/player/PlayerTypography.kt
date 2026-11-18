package app.ember.player

import androidx.compose.material3.Typography
import app.ember.tv.ui.TvTokens
import androidx.compose.ui.unit.sp

private val phoneType=Typography()
private val televisionType=phoneType.copy(
    bodyLarge=phoneType.bodyLarge.copy(fontSize=TvTokens.Type.Player.BodyLarge.sp,lineHeight=TvTokens.Type.Player.BodyLargeLine.sp),
    bodyMedium=phoneType.bodyMedium.copy(fontSize=TvTokens.Type.Player.BodyMedium.sp,lineHeight=TvTokens.Type.Player.BodyMediumLine.sp),
    bodySmall=phoneType.bodySmall.copy(fontSize=TvTokens.Type.Player.BodySmall.sp,lineHeight=TvTokens.Type.Player.BodySmallLine.sp),
    labelLarge=phoneType.labelLarge.copy(fontSize=TvTokens.Type.Player.LabelLarge.sp,lineHeight=TvTokens.Type.Player.LabelLargeLine.sp),
    labelMedium=phoneType.labelMedium.copy(fontSize=TvTokens.Type.Player.LabelMedium.sp,lineHeight=TvTokens.Type.Player.LabelMediumLine.sp),
    labelSmall=phoneType.labelSmall.copy(fontSize=TvTokens.Type.Player.LabelSmall.sp,lineHeight=TvTokens.Type.Player.LabelSmallLine.sp),
    titleLarge=phoneType.titleLarge.copy(fontSize=TvTokens.Type.Player.TitleLarge.sp,lineHeight=TvTokens.Type.Player.TitleLargeLine.sp),
    titleMedium=phoneType.titleMedium.copy(fontSize=TvTokens.Type.Player.TitleMedium.sp,lineHeight=TvTokens.Type.Player.TitleMediumLine.sp),
    titleSmall=phoneType.titleSmall.copy(fontSize=TvTokens.Type.Player.TitleSmall.sp,lineHeight=TvTokens.Type.Player.TitleSmallLine.sp),
)
internal fun playerMaterialTypography(television:Boolean)=if(television)televisionType else phoneType

private fun asTv(type:Typography)=androidx.tv.material3.Typography(
    displayLarge=type.displayLarge,displayMedium=type.displayMedium,displaySmall=type.displaySmall,
    headlineLarge=type.headlineLarge,headlineMedium=type.headlineMedium,headlineSmall=type.headlineSmall,
    titleLarge=type.titleLarge,titleMedium=type.titleMedium,titleSmall=type.titleSmall,
    bodyLarge=type.bodyLarge,bodyMedium=type.bodyMedium,bodySmall=type.bodySmall,
    labelLarge=type.labelLarge,labelMedium=type.labelMedium,labelSmall=type.labelSmall,
)
private val phoneTvType=asTv(phoneType)
private val televisionTvType=asTv(televisionType)
internal fun playerTvTypography(television:Boolean)=if(television)televisionTvType else phoneTvType
