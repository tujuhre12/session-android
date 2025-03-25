package org.thoughtcrime.securesms.reactions.any

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.recyclerview.widget.RecyclerView

class EmojiPickerBehavior<V : View>(
    context: Context,
    attrs: AttributeSet?
) : BottomSheetBehavior<V>(context, attrs) {

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int
    ) {
        // Log scroll parameters for debugging.
        Log.d("EmojiPickerBehavior", "onNestedPreScroll: dy=$dy target=${target.javaClass.simpleName}")

        // Check if the target is a RecyclerView (or your EmojiPickerView if it extends one)
        if (dy > 0 && target is RecyclerView) {
            // Let the RecyclerView handle scrolling if it can scroll up.
            if (target.canScrollVertically(-1)) {
                consumed[1] = 0 // Don't consume; let child scroll.
                return
            }
        }
        super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
    }

    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        Log.d("EmojiPickerBehavior", "onNestedScroll: dyUnconsumed=$dyUnconsumed target=${target.javaClass.simpleName}")

        if (dyUnconsumed < 0 && target is RecyclerView) {
            if (target.canScrollVertically(1)) {
                consumed[1] = 0
                return
            }
        }
        super.onNestedScroll(
            coordinatorLayout, child, target,
            dxConsumed, dyConsumed,
            dxUnconsumed, dyUnconsumed,
            type, consumed
        )
    }
}
