# Pose Marker Alignment Fix Analysis

## Problem
Pose markers appeared misaligned and incorrectly scaled relative to body parts in the camera preview.

## Root Cause

### Aspect Ratio Mismatch
The coordinate mapping assumed normalized [0,1] coordinates map directly to canvas size. This only works when the **image used for detection** and the **canvas/preview** have the **same aspect ratio**.

**Original flow (broken):**
1. Camera provides image → rotated to match display (e.g., 720x1280 after rotation)
2. MediaPipe detects pose → returns normalized [0,1] coordinates relative to **720x1280**
3. Canvas renders overlay → fills screen (e.g., **1080x2400**)
4. Direct mapping: `x * 1080, y * 2400` → **WRONG** because aspect ratios differ (9:16 vs 9:20)

### Specific Issues Found

| Issue | Location | Problem |
|-------|----------|---------|
| Aspect ratio mismatch | `PoseOverlay.kt` | Direct `x * canvasWidth` assumes same aspect ratio |
| No image dimensions | `PoseDetector.kt:224-229` | `imageWidth=0, imageHeight=0` hardcoded |
| Image rotation | `PoseDetector.kt` | Bitmap rotated but dimensions not captured |

## Solution

### Approach: Aspect-Ratio-Aware Coordinate Mapping

The overlay now accounts for the aspect ratio difference between:
- **Source**: The rotated image dimensions used for pose detection
- **Target**: The canvas/preview dimensions on screen

When using `FILL_CENTER`, the preview is scaled and centered. The fix:
1. Captures actual rotated bitmap dimensions in PoseDetector
2. Calculates the scale factor and offset applied by `FILL_CENTER`
3. Applies the same transformation to pose coordinates

### Code Changes

#### 1. PoseDetector.kt

**Added fields to store image dimensions:**
```kotlin
// Store rotated image dimensions for coordinate mapping
private var lastImageWidth = 0
private var lastImageHeight = 0
```

**Capture dimensions after rotation:**
```kotlin
val bitmap = imageProxyToBitmap(imageProxy)

// Store rotated dimensions for coordinate mapping in overlay
lastImageWidth = bitmap.width
lastImageHeight = bitmap.height
```

**Pass actual dimensions in result:**
```kotlin
_latestPose.value = PoseResult(
    landmarks = landmarks,
    timestampMs = timestampMs,
    imageWidth = lastImageWidth,
    imageHeight = lastImageHeight
)
```

#### 2. PoseOverlay.kt

**FILL_CENTER transform calculation:**
```kotlin
private data class FillCenterTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

private fun calculateFillCenterTransform(
    imageWidth: Float,
    imageHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float
): FillCenterTransform {
    if (imageWidth <= 0 || imageHeight <= 0) {
        return FillCenterTransform(scale = 1f, offsetX = 0f, offsetY = 0f)
    }

    val imageAspect = imageWidth / imageHeight
    val canvasAspect = canvasWidth / canvasHeight

    return if (imageAspect > canvasAspect) {
        // Image is wider - scale to fill height, crop sides
        val scale = canvasHeight / imageHeight
        val scaledWidth = imageWidth * scale
        val offsetX = (scaledWidth - canvasWidth) / 2f
        FillCenterTransform(scale, -offsetX, 0f)
    } else {
        // Image is taller - scale to fill width, crop top/bottom
        val scale = canvasWidth / imageWidth
        val scaledHeight = imageHeight * scale
        val offsetY = (scaledHeight - canvasHeight) / 2f
        FillCenterTransform(scale, 0f, -offsetY)
    }
}
```

**Coordinate transformation:**
```kotlin
private fun transformCoordinate(
    normX: Float,
    normY: Float,
    imageWidth: Float,
    imageHeight: Float,
    transform: FillCenterTransform,
    mirrorX: Boolean
): Offset {
    // Apply horizontal mirroring for front camera
    val adjustedNormX = if (mirrorX) 1f - normX else normX

    // Convert normalized [0,1] to image pixel coordinates
    val imageX = adjustedNormX * imageWidth
    val imageY = normY * imageHeight

    // Apply FILL_CENTER transformation (scale + offset)
    val canvasX = imageX * transform.scale + transform.offsetX
    val canvasY = imageY * transform.scale + transform.offsetY

    return Offset(canvasX, canvasY)
}
```

## Coordinate Flow (Fixed)

```
Camera Frame
    │
    ▼
imageProxyToBitmap() ─── Rotates based on imageInfo.rotationDegrees
    │
    ▼
Rotated Bitmap (e.g., 720x1280)
    │
    ├── Store dimensions: lastImageWidth=720, lastImageHeight=1280
    │
    ▼
MediaPipe Pose Detection
    │
    ▼
Normalized Landmarks [0,1] + imageWidth/imageHeight
    │
    ▼
PoseOverlay Canvas
    │
    ├── Calculate FILL_CENTER transform
    │   - Compare image aspect (720/1280=0.5625) vs canvas aspect (1080/2400=0.45)
    │   - Image taller → scale=1080/720=1.5, offsetY=(1920-2400)/2=-240
    │
    ▼
transformCoordinate()
    │
    ├── imageX = normX * 720
    ├── imageY = normY * 1280
    ├── canvasX = imageX * 1.5 + 0
    ├── canvasY = imageY * 1.5 + (-240)
    │
    ▼
Correctly Aligned Markers on Screen
```

## Files Modified

1. `app/src/main/java/com/biomechanix/movementor/sme/ml/PoseDetector.kt`
2. `app/src/main/java/com/biomechanix/movementor/sme/ui/components/PoseOverlay.kt`

## Testing Checklist

- [ ] Front camera (portrait mode) - markers align with body parts
- [ ] Back camera - markers align correctly
- [ ] Different device aspect ratios (16:9, 18:9, 19.5:9, 20:9)
- [ ] Landscape orientation (if supported)
- [ ] Mirroring works correctly for front camera

## Related Issues Fixed

1. **90-degree rotation** - Fixed by `imageProxyToBitmap()` using `imageInfo.rotationDegrees`
2. **Scaling mismatch** - Fixed by `calculateFillCenterTransform()`
3. **Missing dimensions** - Fixed by capturing `lastImageWidth/Height` after rotation
