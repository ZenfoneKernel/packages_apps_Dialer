/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.list;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.GridView;
import android.widget.ImageView;

import com.android.dialer.R;
import com.android.dialer.list.DragDropController.DragItemContainer;

/**
 * Viewgroup that presents the user's speed dial contacts in a grid.
 */
public class PhoneFavoriteListView extends GridView {

    public static final String LOG_TAG = PhoneFavoriteListView.class.getSimpleName();

    private float mTouchSlop;

    private int mTopScrollBound;
    private int mBottomScrollBound;

    private Handler mScrollHandler;
    private final long SCROLL_HANDLER_DELAY_MILLIS = 5;

    private int mAnimationDuration;

    final int[] mLocationOnScreen = new int[2];

    // X and Y offsets inside the item from where the user grabbed to the
    // child's left coordinate. This is used to aid in the drawing of the drag shadow.
    private int mTouchOffsetToChildLeft;
    private int mTouchOffsetToChildTop;

    /**
     * {@link #mTopScrollBound} and {@link mBottomScrollBound} will be
     * offseted to the top / bottom by {@link #getHeight} * {@link #BOUND_GAP_RATIO} pixels.
     */
    private final float BOUND_GAP_RATIO = 0.2f;

    public PhoneFavoriteListView(Context context) {
        this(context, null);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs) {
        this(context, attrs, -1);
    }

    public PhoneFavoriteListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mAnimationDuration = context.getResources().getInteger(R.integer.fade_duration);
        mTouchSlop = ViewConfiguration.get(context).getScaledPagingTouchSlop();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
    }

    /**
     * TODO: This is all swipe to remove code (nothing to do with drag to remove). This should
     * be cleaned up and removed once drag to remove becomes the only way to remove contacts.
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return super.onInterceptTouchEvent(ev);
    }

    /**
     * Find the view under the pointer.
     */
    private View getViewAtPosition(int x, int y) {
        final int count = getChildCount();
        View child;
        for (int childIdx = 0; childIdx < count; childIdx++) {
            child = getChildAt(childIdx);
            if (y >= child.getTop() && y <= child.getBottom() && x >= child.getLeft()
                    && x <= child.getRight()) {
                return child;
            }
        }
        return null;
    }

    private void ensureScrollHandler() {
        if (mScrollHandler == null) {
            mScrollHandler = getHandler();
        }
    }
}
