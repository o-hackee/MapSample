package ol.ko.mapsample

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.MotionEvent

import org.osmdroid.views.MapView
import org.osmdroid.views.Projection
import org.osmdroid.views.overlay.Overlay

data class MyIconPosition(
    val alignBottom: Boolean,
    val alignRight: Boolean,
    val verticalOffset: Int = MyClickableIconOverlay.DEFAULT_OFFSET,
    val horizontalOffset: Int = MyClickableIconOverlay.DEFAULT_OFFSET
) {
}

class MyClickableIconOverlay(
    val id: Int,
    private val icon: Drawable,
    private val iconPosition: MyIconPosition = MyIconPosition(alignBottom = true, alignRight = true, verticalOffset = DEFAULT_OFFSET, horizontalOffset = DEFAULT_OFFSET),
    private val onIconClicked: (mapView: MapView, iconId: Int) -> Boolean
) : Overlay() {

    companion object {
        const val DEFAULT_OFFSET = 50
    }

    private var verticalOffset: Int = 0
    private var horizontalOffset: Int = 0

    /**
     * Draw the icon.
     */
    override fun draw(canvas: Canvas, projection: Projection) {
        val mapRect = projection.intrinsicScreenRect
        icon.bounds = Rect(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)

        verticalOffset = if (iconPosition.alignBottom) {
            mapRect.height() - iconPosition.verticalOffset - icon.bounds.height()
        } else
            iconPosition.verticalOffset
        horizontalOffset = if (iconPosition.alignRight) {
            mapRect.width() - iconPosition.horizontalOffset - icon.bounds.width()
        } else
            iconPosition.horizontalOffset

        canvas.save()
        canvas.translate(horizontalOffset.toFloat(), verticalOffset.toFloat())
        icon.draw(canvas)
        canvas.restore()
    }

    /**
     * @return true: tap handled. No following overlay/map should handle the event.
     * false: tap not handled. A following overlay/map should handle the event.
     */
    override fun onSingleTapConfirmed(event: MotionEvent, mapView: MapView): Boolean {
        val touched = hitTest(event)
        return if (touched) {
            onIconClicked(mapView, id)
        } else {
            super.onSingleTapConfirmed(event, mapView)
        }
    }

    private fun hitTest(event: MotionEvent): Boolean {
        val y = event.y.toInt() - verticalOffset
        val x = event.x.toInt() - horizontalOffset
        return icon.bounds.contains(x, y)
    }
}