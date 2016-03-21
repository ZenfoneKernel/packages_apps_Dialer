/*
 * Copyright (C) 2016 Exodus Android
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

import static android.Manifest.permission.READ_CONTACTS;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.dialer.util.IntentUtil;
import com.android.contacts.common.activity.TransactionSafeActivity;
import com.android.contacts.common.ContactPhotoManager;
import com.android.contacts.common.ContactTileLoaderFactory;
import com.android.contacts.common.list.ContactTileView;
import com.android.contacts.common.list.OnPhoneNumberPickerActionListener;
import com.android.contacts.common.util.PermissionsUtil;
import com.android.dialer.R;
import com.android.dialer.interactions.PhoneNumberInteraction;
import com.android.dialer.util.DialerUtils;
import com.android.dialer.widget.EmptyContentView;
import com.android.internal.util.exodus.DeviceUtils;

import java.util.ArrayList;
import java.util.HashMap;

public class SpeedDialActivity extends TransactionSafeActivity implements OnItemClickListener,
        PhoneFavoritesTileAdapter.OnDataSetChangedForAnimationListener,
        EmptyContentView.OnEmptyViewActionButtonClickedListener {

    private static final String TAG = SpeedDialActivity.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final String CALL_ORIGIN_DIALTACTS = "com.android.dialer.DialtactsActivity";

    private static final int READ_CONTACTS_PERMISSION_REQUEST_CODE = 1;

    private static final long KEY_REMOVED_ITEM_HEIGHT = Long.MAX_VALUE;

    private int mAnimationDuration;

    private static int LOADER_ID_CONTACT_TILE = 1;

    private PhoneFavoritesTileAdapter mContactTileAdapter;

    private PhoneFavoriteListView mListView;
    private View mContactTileFrame;
    private EmptyContentView mEmptyView;

    private boolean mActive = false;

    private final HashMap<Long, Integer> mItemIdTopMap = new HashMap<Long, Integer>();
    private final HashMap<Long, Integer> mItemIdLeftMap = new HashMap<Long, Integer>();

    private final ContactTileView.Listener mContactTileAdapterListener =
            new ContactTileAdapterListener();
    private final LoaderManager.LoaderCallbacks<Cursor> mContactTileLoaderListener =
            new ContactTileLoaderListener();

    private class ContactTileLoaderListener implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public CursorLoader onCreateLoader(int id, Bundle args) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onCreateLoader.");
            return ContactTileLoaderFactory.createStrequentPhoneOnlyLoader(SpeedDialActivity.this);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoadFinished");
            mContactTileAdapter.setContactCursor(data);
            setEmptyViewVisibility(mContactTileAdapter.getCount() == 0);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (DEBUG) Log.d(TAG, "ContactTileLoaderListener#onLoaderReset. ");
        }
    }

    private class ContactTileAdapterListener implements ContactTileView.Listener {
        @Override
        public void onContactSelected(Uri contactUri, Rect targetRect) {
            Log.e(TAG, "contactUri = " + contactUri);
            mPhoneNumberPickerActionListener.onPickPhoneNumberAction(contactUri);
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            Log.e(TAG, "phoneNumber = " + phoneNumber);
            mPhoneNumberPickerActionListener.onCallNumberDirectly(phoneNumber);
        }

        @Override
        public int getApproximateTileWidth() {
            return 200;
        }
    }

    OnPhoneNumberPickerActionListener mPhoneNumberPickerActionListener = new OnPhoneNumberPickerActionListener() {
        @Override
        public void onPickPhoneNumberAction(Uri dataUri) {
            PhoneNumberInteraction.startInteractionForPhoneCall(
                    SpeedDialActivity.this, dataUri, CALL_ORIGIN_DIALTACTS);
            finishActivity();
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber) {
            onCallNumberDirectly(phoneNumber, false /* isVideoCall */);
        }

        @Override
        public void onCallNumberDirectly(String phoneNumber, boolean isVideoCall) {
            if (phoneNumber == null) {
                phoneNumber = "";
            }
            Intent intent = isVideoCall ?
                    IntentUtil.getVideoCallIntent(phoneNumber, CALL_ORIGIN_DIALTACTS) :
                    IntentUtil.getCallIntent(phoneNumber, CALL_ORIGIN_DIALTACTS);
            DialerUtils.startActivityWithErrorToast(SpeedDialActivity.this, intent);
            finishActivity();
        }

        @Override
        public void onShortcutIntentCreated(Intent intent) {

        }

        @Override
        public void onHomeInActionBarSelected() {
            SpeedDialActivity.this.onBackPressed();
        }

    };

    @Override
    public void onCreate(Bundle savedState) {
        if (DEBUG) Log.d(TAG, "onCreate()");

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        super.onCreate(savedState);
        setContentView(R.layout.speed_dial_activity);

        mContactTileAdapter = new PhoneFavoritesTileAdapter(this, mContactTileAdapterListener,
                this);
        mContactTileAdapter.setPhotoLoader(ContactPhotoManager.getInstance(this));

        if (PermissionsUtil.hasContactsPermissions(this)) {
            getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null, mContactTileLoaderListener);
        } else {
            setEmptyViewVisibility(true);
        }

        mListView = (PhoneFavoriteListView) findViewById(R.id.contact_tile_list);
        mListView.setOnItemClickListener(this);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setVerticalScrollbarPosition(View.SCROLLBAR_POSITION_RIGHT);
        mListView.setScrollBarStyle(ListView.SCROLLBARS_OUTSIDE_OVERLAY);

        mEmptyView = (EmptyContentView) findViewById(R.id.empty_list_view);
        mEmptyView.setImage(R.drawable.empty_speed_dial);
        mEmptyView.setActionClickedListener(this);

        mContactTileFrame = findViewById(R.id.contact_tile_frame);

        final LayoutAnimationController controller = new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
        controller.setDelay(0);
        mListView.setLayoutAnimation(controller);
        mListView.setAdapter(mContactTileAdapter);

        mListView.setFastScrollEnabled(false);
        mListView.setFastScrollAlwaysVisible(false);

        mAnimationDuration = getResources().getInteger(R.integer.fade_duration);

        FrameLayout touchOutside = (FrameLayout) findViewById(R.id.activity_layout);
        touchOutside.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                finishActivity();
                return true;
            }
        });

        FrameLayout touchInside = (FrameLayout) findViewById(R.id.contact_tile_frame);
        touchInside.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                finishActivity();
                return true;
            }
        });
    }

    void finishActivity() {
        final Activity activity = this;
        if (activity == null) {
            return;
        }
        this.finish();
    }

    @Override
    public void onStart() {
        super.onStart();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onResume() {
        super.onResume();
        overridePendingTransition(0, 0);

        if (PermissionsUtil.hasContactsPermissions(this)) {
            if (getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE) == null) {
                getLoaderManager().initLoader(LOADER_ID_CONTACT_TILE, null,
                        mContactTileLoaderListener);

            } else {
                getLoaderManager().getLoader(LOADER_ID_CONTACT_TILE).forceLoad();
            }

            mEmptyView.setDescription(R.string.speed_dial_empty);
            mEmptyView.setActionLabel(R.string.speed_dial_empty_add_favorite_action);
        } else {
            mEmptyView.setDescription(R.string.permission_no_speeddial);
            mEmptyView.setActionLabel(R.string.permission_single_turn_on);
        }
        
        Log.e(TAG, "mActive is " + mActive);
        
        if (mActive) {
			mActive = false;
			finishActivity();
		} else {
			mActive = true;
		}
    }

    public boolean hasFrequents() {
        if (mContactTileAdapter == null) return false;
        return mContactTileAdapter.getNumFrequents() > 0;
    }

    /* package */ void setEmptyViewVisibility(final boolean visible) {
        final int previousVisibility = mEmptyView.getVisibility();
        final int emptyViewVisibility = visible ? View.VISIBLE : View.GONE;
        final int listViewVisibility = visible ? View.GONE : View.VISIBLE;

        if (previousVisibility != emptyViewVisibility) {
            final FrameLayout.LayoutParams params = (LayoutParams) mContactTileFrame
                    .getLayoutParams();
            params.height = visible ? LayoutParams.WRAP_CONTENT : LayoutParams.MATCH_PARENT;
            mContactTileFrame.setLayoutParams(params);
            mEmptyView.setVisibility(emptyViewVisibility);
            mListView.setVisibility(listViewVisibility);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        final int contactTileAdapterCount = mContactTileAdapter.getCount();
        if (position <= contactTileAdapterCount) {
            Log.e(TAG, "onItemClick() event for unexpected position. "
                    + "The position " + position + " is before \"all\" section. Ignored.");
        }
    }

    private void saveOffsets(int removedItemHeight) {
        final int firstVisiblePosition = mListView.getFirstVisiblePosition();
        if (DEBUG) {
            Log.d(TAG, "Child count : " + mListView.getChildCount());
        }
        for (int i = 0; i < mListView.getChildCount(); i++) {
            final View child = mListView.getChildAt(i);
            final int position = firstVisiblePosition + i;

            if (!mContactTileAdapter.isIndexInBound(position)) {
                continue;
            }
            final long itemId = mContactTileAdapter.getItemId(position);
            if (DEBUG) {
                Log.d(TAG, "Saving itemId: " + itemId + " for listview child " + i + " Top: "
                        + child.getTop());
            }
            mItemIdTopMap.put(itemId, child.getTop());
            mItemIdLeftMap.put(itemId, child.getLeft());
        }
        mItemIdTopMap.put(KEY_REMOVED_ITEM_HEIGHT, removedItemHeight);
    }

    private void animateGridView(final long... idsInPlace) {
        if (mItemIdTopMap.isEmpty()) {
            return;
        }

        final ViewTreeObserver observer = mListView.getViewTreeObserver();
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @SuppressWarnings("unchecked")
            @Override
            public boolean onPreDraw() {
                observer.removeOnPreDrawListener(this);
                final int firstVisiblePosition = mListView.getFirstVisiblePosition();
                final AnimatorSet animSet = new AnimatorSet();
                final ArrayList<Animator> animators = new ArrayList<Animator>();
                for (int i = 0; i < mListView.getChildCount(); i++) {
                    final View child = mListView.getChildAt(i);
                    int position = firstVisiblePosition + i;

                    if (!mContactTileAdapter.isIndexInBound(position)) {
                        continue;
                    }

                    final long itemId = mContactTileAdapter.getItemId(position);

                    if (containsId(idsInPlace, itemId)) {
                        animators.add(ObjectAnimator.ofFloat(
                                child, "alpha", 0.0f, 1.0f));
                        break;
                    } else {
                        Integer startTop = mItemIdTopMap.get(itemId);
                        Integer startLeft = mItemIdLeftMap.get(itemId);
                        final int top = child.getTop();
                        final int left = child.getLeft();
                        int deltaX = 0;
                        int deltaY = 0;

                        if (startLeft != null) {
                            if (startLeft != left) {
                                deltaX = startLeft - left;
                                animators.add(ObjectAnimator.ofFloat(
                                        child, "translationX", deltaX, 0.0f));
                            }
                        }

                        if (startTop != null) {
                            if (startTop != top) {
                                deltaY = startTop - top;
                                animators.add(ObjectAnimator.ofFloat(
                                        child, "translationY", deltaY, 0.0f));
                            }
                        }

                        if (DEBUG) {
                            Log.d(TAG, "Found itemId: " + itemId + " for listview child " + i +
                                    " Top: " + top +
                                    " Delta: " + deltaY);
                        }
                    }
                }

                if (animators.size() > 0) {
                    animSet.setDuration(mAnimationDuration).playTogether(animators);
                    animSet.start();
                }

                mItemIdTopMap.clear();
                mItemIdLeftMap.clear();
                return true;
            }
        });
    }

    @Override
    public void onDataSetChangedForAnimation(long... idsInPlace) {
        animateGridView(idsInPlace);
    }

    @Override
    public void cacheOffsetsForDatasetChange() {
        saveOffsets(0);
    }

    boolean containsId(long[] ids, long target) {
        for (int i = 0; i < ids.length; i++) {
            if (ids[i] == target) {
                return true;
            }
        }
        return false;
    }

    public AbsListView getListView() {
        return mListView;
    }

    @Override
    public void onEmptyViewActionButtonClicked() {

        if (!PermissionsUtil.hasPermission(this, READ_CONTACTS)) {
            requestPermissions(new String[] {READ_CONTACTS}, READ_CONTACTS_PERMISSION_REQUEST_CODE);
        }

        if (DeviceUtils.isPackageInstalled(this, "com.android.contacts")) {
            try {
                openContactApplication("com.android.contacts");
            } catch (Exception e) {
            }
        } else if (DeviceUtils.isPackageInstalled(this, "com.google.android.contacts")) {
            try {
                openContactApplication("com.google.android.contacts");
            } catch (Exception e) {
            }
        } else {
            Toast.makeText(this, R.string.no_contact_application, Toast.LENGTH_SHORT).show();
        }
        finishActivity();
    }

    private boolean openContactApplication(String packageName) {
        PackageManager manager = this.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            if (i == null) {
                return false;
            }

            i.addCategory(Intent.CATEGORY_LAUNCHER);
            this.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode == READ_CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.length == 1 && PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                PermissionsUtil.notifyPermissionGranted(this, READ_CONTACTS);
            }
        }
    }
}


