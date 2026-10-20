package com.gathin.tvhome;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * The greeting line: the greeting text first, then the weather. The children after the first are measured at
 * their natural width before the first gets what is left, so on a narrow top bar it is the greeting that is
 * ellipsized and the temperature stays whole. Children sit left to right, vertically centred, in the order
 * they are declared (the launcher is not mirrored for right-to-left languages, see activity_main.xml).
 */
public class GreetingRow extends ViewGroup {

    public GreetingRow(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int used = 0, height = 0;
        for (int i = getChildCount() - 1; i >= 0; i--) {   // the trailing children first, the greeting last
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            measureChild(child, MeasureSpec.makeMeasureSpec(Math.max(0, width - used), MeasureSpec.AT_MOST), heightSpec);
            used += child.getMeasuredWidth();
            height = Math.max(height, child.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(used, widthSpec), resolveSize(height, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int x = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth(), h = child.getMeasuredHeight();
            int y = (height - h) / 2;
            child.layout(x, y, x + w, y + h);
            x += w;
        }
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }
}
