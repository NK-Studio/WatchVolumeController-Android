package com.nkstudio.pc_volume_controller

import androidx.wear.tiles.*
import androidx.wear.tiles.DimensionBuilders.dp
import androidx.wear.tiles.LayoutElementBuilders.Image
import androidx.wear.tiles.ResourceBuilders.AndroidImageResourceByResId
import androidx.wear.tiles.ResourceBuilders.ImageResource
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val ID_VolumeIcon = "IC_Volume"

class MyService : TileService()
{
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTimeline(
                    TimelineBuilders.Timeline.Builder().addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder().setLayout(
                            LayoutElementBuilders.Layout.Builder().setRoot(
                                tappableElement()
                            ).build()
                        ).build()
                    ).build()
                ).build()
        )

    override fun onResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources>
    {
        return Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .addIdToImageMapping(
                    ID_VolumeIcon,
                    ImageResource.Builder().setAndroidResourceByResId(
                        AndroidImageResourceByResId.Builder().setResourceId(R.drawable.sound).build()
                    ).build()
                )
                .setVersion(RESOURCES_VERSION)
                .build()
        )
    }

    private fun tappableElement(): LayoutElementBuilders.LayoutElement =
        Image.Builder().setWidth(dp(80f)).setHeight(dp(80f)).setResourceId(ID_VolumeIcon)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId("PCController")
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setClassName(MainActivity::class.java.name)
                                            .setPackageName(this.packageName)
                                            .build()
                                    ).build()
                            ).build()
                    ).build()
            ).build()
}