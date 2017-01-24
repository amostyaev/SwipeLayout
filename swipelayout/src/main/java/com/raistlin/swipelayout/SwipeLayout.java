package com.raistlin.swipelayout;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

public class SwipeLayout extends FrameLayout {

    private SwipeViewObserver mViewObserver = new SwipeViewObserver();
    private ViewDragHelper mDragHelper;

    private View mAvailableView;
    private View mCurrentView;

    private boolean mDragSet = false;
    private boolean mSwitchViews = false;
    private boolean mBothViewsVisible = false;

    public SwipeLayout(Context context) {
        super(context);
        init();
    }

    public SwipeLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwipeLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mDragHelper = ViewDragHelper.create(this, 1f, new SwipeDragCallback());
    }

    public boolean isSwipeEnabled() {
        return mBothViewsVisible;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mCurrentView = mAvailableView = null;
        initViews();
        getViewTreeObserver().addOnGlobalLayoutListener(mViewObserver);
    }

    private void initViews() {
        final int childCount = getChildCount();
        if (childCount != 2) throw new RuntimeException("SwipeLayout needs exactly two views!");

        for (int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            child.setTag(child.getVisibility());
            if (mCurrentView == null && child.getVisibility() != GONE) {
                mCurrentView = child;
            } else {
                mAvailableView = child;
            }
        }

        if (mCurrentView == null) throw new RuntimeException("Current view must be added!");
        if (mAvailableView == null) throw new RuntimeException("Available view must be added!");

        mBothViewsVisible = mAvailableView.getVisibility() != GONE;
        offsetViewLeftRight(mAvailableView, getWidth() - mAvailableView.getLeft());
    }

    private void startScrollAnimation(View view, int targetX) {
        if (mDragHelper.settleCapturedViewAt(targetX + getViewLeftMargin(view), view.getTop()/* + getViewTopMargin(view)*/)) {
            ViewCompat.postOnAnimation(view, new SettleRunnable(view));
        }
    }

    private void completeAnimation() {
        if (mSwitchViews) {
            View tmp = mAvailableView;
            mAvailableView = mCurrentView;
            mCurrentView = tmp;
            mSwitchViews = false;
        }
        offsetViewLeftRight(mCurrentView, getLeft() - mCurrentView.getLeft());
        if (mAvailableView.getLeft() < 0) {
            offsetViewLeftRight(mAvailableView, -mAvailableView.getLeft() - getWidth());
        } else {
            offsetViewLeftRight(mAvailableView, getWidth() - mAvailableView.getLeft());
        }
    }

    private void offsetChildren(int dx) {
        if (dx == 0) return;

        if (!mDragSet) {
            if (mAvailableView.getLeft() < 0 && dx < 0) {
                offsetViewLeftRight(mAvailableView, getWidth() - mAvailableView.getLeft());
            } else if (mAvailableView.getLeft() > 0 && dx > 0) {
                offsetViewLeftRight(mAvailableView, -mAvailableView.getLeft() - getWidth());
            }
            mDragSet = true;
        }

        ViewCompat.offsetLeftAndRight(mAvailableView, dx);
        invalidate(mAvailableView.getLeft(), mAvailableView.getTop(), mAvailableView.getRight(), mAvailableView.getBottom());
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return isSwipeEnabled()
                ? mDragHelper.shouldInterceptTouchEvent(event)
                : super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isSwipeEnabled()) {
            return super.onTouchEvent(event);
        } else {
            mDragHelper.processTouchEvent(event);
            super.onTouchEvent(event);
            return true;
        }
    }

    private static void offsetViewLeftRight(View view, int offset) {
        ViewCompat.offsetLeftAndRight(view, offset + getViewLeftMargin(view));
    }

    private static int getViewLeftMargin(View view) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        return lp.leftMargin;
    }

    private class SwipeDragCallback extends ViewDragHelper.Callback {

        private int mLeftStart;

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            mDragSet = false;
            mLeftStart = child.getLeft();
            return child == mCurrentView;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top - dy; // return original Y position, before drag
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return left;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return getWidth();
        }

        @Override
        public void onViewReleased(View releasedChild, float velX, float velY) {
            int dx = mCurrentView.getLeft() - mLeftStart;
            if (dx == 0) return;

            mSwitchViews = false;
            if (dx > 0 && mAvailableView.getRight() > mAvailableView.getWidth() / 5) {
                mSwitchViews = true;
                startScrollAnimation(mCurrentView, getWidth());
            } else if (dx < 0 && mAvailableView.getLeft() + mAvailableView.getWidth() / 5 < getWidth()) {
                mSwitchViews = true;
                startScrollAnimation(mCurrentView, -getWidth());
            } else {
                startScrollAnimation(mCurrentView, getLeft());
            }
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            offsetChildren(dx);
        }

    }

    private class SettleRunnable implements Runnable {

        private final View mView;

        SettleRunnable(View view) {
            mView = view;
        }

        public void run() {
            if (mDragHelper != null && mDragHelper.continueSettling(true)) {
                ViewCompat.postOnAnimation(mView, this);
            } else {
                completeAnimation();
            }
        }
    }

    private class SwipeViewObserver implements ViewTreeObserver.OnGlobalLayoutListener {

        @SuppressWarnings("WrongConstant")
        @Override
        public void onGlobalLayout() {
            if ((int) mCurrentView.getTag() != mCurrentView.getVisibility()
                    || (int) mAvailableView.getTag() != mAvailableView.getVisibility()) {
                initViews();
            }
        }
    }
}
