package com.biomechanix.movementor.sme.data.local.db

import androidx.room.TypeConverter
import com.biomechanix.movementor.sme.domain.model.setup.CameraView
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane

/**
 * Room type converters for custom types.
 */
class Converters {

    // ========================================
    // MOVEMENT PLANE
    // ========================================

    @TypeConverter
    fun fromMovementPlane(value: MovementPlane): String {
        return value.name
    }

    @TypeConverter
    fun toMovementPlane(value: String): MovementPlane {
        return MovementPlane.valueOf(value)
    }

    // ========================================
    // CAMERA VIEW
    // ========================================

    @TypeConverter
    fun fromCameraView(value: CameraView): String {
        return value.name
    }

    @TypeConverter
    fun toCameraView(value: String): CameraView {
        return CameraView.valueOf(value)
    }
}
