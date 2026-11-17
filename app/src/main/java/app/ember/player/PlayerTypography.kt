package app.ember.player

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.sp

private val phoneType=Typography()
private val televisionType=phoneType.copy(
    bodyLarge=phoneType.bodyLarge.copy(fontSize=20.sp,lineHeight=28.sp),
    bodyMedium=phoneType.bodyMedium.copy(fontSize=18.sp,lineHeight=26.sp),
    bodySmall=phoneType.bodySmall.copy(fontSize=16.sp,lineHeight=24.sp),
    labelLarge=phoneType.labelLarge.copy(fontSize=18.sp,lineHeight=26.sp),
    labelMedium=phoneType.labelMedium.copy(fontSize=16.sp,lineHeight=24.sp),
    labelSmall=phoneType.labelSmall.copy(fontSize=14.sp,lineHeight=22.sp),
    titleLarge=phoneType.titleLarge.copy(fontSize=26.sp,lineHeight=34.sp),
    titleMedium=phoneType.titleMedium.copy(fontSize=22.sp,lineHeight=30.sp),
    titleSmall=phoneType.titleSmall.copy(fontSize=20.sp,lineHeight=28.sp),
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
