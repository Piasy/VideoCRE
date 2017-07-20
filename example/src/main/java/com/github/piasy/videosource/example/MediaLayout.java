package com.github.piasy.videosource.example;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Created by powerinfo on 21/04/2017.
 */
public class MediaLayout extends FrameLayout {
    private ViewDragHelper mDragHelper;

    public MediaLayout(@NonNull final Context context) {
        super(context);
    }

    public MediaLayout(@NonNull final Context context,
            @Nullable final AttributeSet attrs) {
        super(context, attrs);
    }

    public MediaLayout(@NonNull final Context context, @Nullable final AttributeSet attrs,
            final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mDragHelper.cancel();
            return false;
        }
        return mDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        mDragHelper = ViewDragHelper.create(this, 1.0F, new DragCallback());
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        mDragHelper.processTouchEvent(ev);
        return true;
    }

    class DragCallback extends ViewDragHelper.Callback {
        @Override
        public boolean tryCaptureView(final View child, final int pointerId) {
            ViewGroup.LayoutParams params = child.getLayoutParams();
            return pointerId == 0
                   && params.width != ViewGroup.LayoutParams.MATCH_PARENT
                   && params.height != ViewGroup.LayoutParams.MATCH_PARENT;
        }

        @Override
        public int clampViewPositionHorizontal(final View child, final int left, final int dx) {
            int leftBound = getPaddingLeft();
            int rightBound = getWidth() - getPaddingRight() - child.getWidth();
            return Math.min(Math.max(left, leftBound), rightBound);
        }

        @Override
        public int clampViewPositionVertical(final View child, final int top, final int dy) {
            int topBound = getPaddingTop();
            int bottomBound = getHeight() - getPaddingBottom() - child.getHeight();
            return Math.min(Math.max(top, topBound), bottomBound);
        }
    }
}
