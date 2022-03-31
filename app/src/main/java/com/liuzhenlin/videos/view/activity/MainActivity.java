/*
 * Created on 2017/09/26.
 * Copyright © 2017–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.MarginLayoutParamsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.util.Synthetic;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.liuzhenlin.circularcheckbox.Utils;
import com.liuzhenlin.common.adapter.BaseAdapter2;
import com.liuzhenlin.common.listener.OnBackPressedListener;
import com.liuzhenlin.common.utils.BitmapUtils;
import com.liuzhenlin.common.utils.ColorUtils;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.OSHelper;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.utils.TextViewUtils;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.common.utils.UiUtils;
import com.liuzhenlin.common.view.ScrollDisableListView;
import com.liuzhenlin.common.view.ScrollDisableViewPager;
import com.liuzhenlin.common.view.SwipeRefreshLayout;
import com.liuzhenlin.floatingmenu.DensityUtils;
import com.liuzhenlin.slidingdrawerlayout.SlidingDrawerLayout;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppPrefs;
import com.liuzhenlin.videos.utils.MergeAppUpdateChecker;
import com.liuzhenlin.videos.view.fragment.LocalVideosFragment;
import com.liuzhenlin.videos.web.youtube.WebService;
import com.liuzhenlin.videos.web.youtube.YoutubeFragment;
import com.taobao.sophix.SophixManager;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import static com.liuzhenlin.common.Consts.EMPTY_STRING;
import static com.liuzhenlin.videos.Consts.TEXT_COLOR_PRIMARY_DARK;
import static com.liuzhenlin.videos.Consts.TEXT_COLOR_PRIMARY_LIGHT;

/**
 * @author 刘振林
 */
public class MainActivity extends BaseActivity implements View.OnClickListener,
        AdapterView.OnItemClickListener, SlidingDrawerLayout.OnDrawerScrollListener,
        LocalVideosFragment.InteractionCallback {

    @SuppressLint("StaticFieldLeak")
    @Nullable
    static MainActivity this$;

    @Synthetic final Fragment[] mFragments = new Fragment[2];
    private static final int INDEX_LOCAL_VIDEOS_FRAGMENT = 0;
    private static final int INDEX_YOUTUBE_FRAGMENT = 1;

    @Synthetic SlidingDrawerLayout mSlidingDrawerLayout;

    @Synthetic ScrollDisableViewPager mFragmentViewPager;
    private ViewGroup mActionBarContainer;
    private TabLayout mFragmentTabLayout;

    // LocalVideosFragment和OnlineVideosFragment的ActionBar
    private ViewGroup mActionBar;
    private ImageButton mHomeAsUpIndicator;
    private TextView mTitleText;
    @Synthetic ImageButton mActionButton;
    private DrawerArrowDrawable mDrawerArrowDrawable;
    private final MainActivityToolbarActions mToolbarActions = new MainActivityToolbarActions(this);

    // 临时缓存LocalSearchedVideosFragment或LocalFoldedVideosFragment的ActionBar
    private ViewGroup mTmpActionBar;

    @Synthetic ScrollDisableListView mDrawerList;
    @Synthetic DrawerListAdapter mDrawerListAdapter;
    @Synthetic ImageView mDrawerImage;
    @Synthetic boolean mIsDrawerStatusLight;
    @Synthetic boolean mIsDrawerListForegroundLight;
    private float mDrawerScrollPercent;

    private static final int REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE = 7;
    private static final int REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION = 8;

    @Synthetic String mCheckUpdateResultText;
    private String mIsTheLatestVersion;
    @Synthetic String mFindNewVersion;
    private MergeAppUpdateChecker.OnResultListener mOnCheckUpdateResultListener;

    private boolean mIsBackPressed;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        // 有新的热更新补丁可用时，加载...
        SophixManager.getInstance().queryAndLoadNewPatch();

//        // 打开应用时自动检测更新（有悬浮窗权限时才去检查，不然弹不出更新提示对话框）
//        checkUpdateIfPermissionGranted(false);
        checkUpdate(false);

        mIsTheLatestVersion = getString(R.string.isTheLatestVersion);
        mFindNewVersion = getString(R.string.findNewVersion);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this$ = this;

        setAsNonSwipeBackActivity();
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Window window = getWindow();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SystemBarUtils.setTransparentStatus(window);
            } else {
                SystemBarUtils.setTranslucentStatus(window, true);
            }
        }
        if (savedInstanceState == null) {
            mFragments[INDEX_LOCAL_VIDEOS_FRAGMENT] = new LocalVideosFragment();
            mFragments[INDEX_YOUTUBE_FRAGMENT] = new YoutubeFragment();
        } else {
            for (Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (fragment instanceof LocalVideosFragment) {
                    mFragments[INDEX_LOCAL_VIDEOS_FRAGMENT] = fragment;
                } else if (fragment instanceof YoutubeFragment) {
                    mFragments[INDEX_YOUTUBE_FRAGMENT] = fragment;
                }
            }
        }
        initViews();
    }

    private void initViews() {
        final App app = App.getInstance(this);
        final int screenWidth = app.getScreenWidthIgnoreOrientation();

        mSlidingDrawerLayout = findViewById(R.id.slidingDrawerLayout);
        mSlidingDrawerLayout.setStartDrawerWidthPercent(
                1f - (app.getVideoThumbWidth() + DensityUtils.dp2px(app, 20f)) / (float) screenWidth);
        mSlidingDrawerLayout.addOnDrawerScrollListener(new SlidingDrawerLayout.SimpleOnDrawerScrollListener() {
            @Override
            public void onScrollStateChange(
                    @NonNull SlidingDrawerLayout parent, @NonNull View drawer, int state) {
                parent.removeOnDrawerScrollListener(this);

                mDrawerList = drawer.findViewById(R.id.list_drawer);
                mDrawerList.setDivider(null);
//                View divider = new ViewStub(app);
//                mDrawerList.addHeaderView(divider);
//                mDrawerList.addFooterView(divider);
                mDrawerList.setAdapter(mDrawerListAdapter = new DrawerListAdapter());
                mDrawerList.setOnItemClickListener(MainActivity.this);
                mDrawerList.setScrollEnabled(false);

                mDrawerImage = findViewById(R.id.image_drawer);
                AppPrefs asp = AppPrefs.getSingleton(app);
                final String path = asp.getDrawerBackgroundPath();
                // 未设置背景图片
                if (path == null) {
                    setLightDrawerStatus(asp.isLightDrawerStatus());
                    mDrawerListAdapter.setLightDrawerListForeground(asp.isLightDrawerListForeground());

                } else if (new File(path).exists()) {
                    setDrawerBackground(path);
                    // 用户从存储卡中删除了该路径下的图片或其路径已改变
                } else {
                    asp.setDrawerBackgroundPath(null);
                    asp.edit()
                            .setLightDrawerStatus(false, true)
                            .setLightDrawerStatus(true, false)
                            .setLightDrawerListForeground(false, false)
                            .setLightDrawerListForeground(true, true)
                            .apply();
                }
            }
        });
        mSlidingDrawerLayout.addOnDrawerScrollListener(this);
        mSlidingDrawerLayout.addOnDrawerScrollListener(getLocalVideosFragment());
//        mSlidingDrawerLayout.addOnDrawerScrollListener(mOnlineVideosFragment);

        mActionBarContainer = findViewById(R.id.container_actionbar);
        mActionBar = findViewById(R.id.actionbar);
        insertTopPaddingToActionBarIfNeeded(mActionBar);

        mActionButton = mActionBar.findViewById(R.id.img_btn);
        mActionButton.setOnClickListener(this);

        mDrawerArrowDrawable = new DrawerArrowDrawable(this);
        mDrawerArrowDrawable.setGapSize(12.5f);
        mDrawerArrowDrawable.setColor(Color.WHITE);

        mHomeAsUpIndicator = mActionBar.findViewById(R.id.btn_homeAsUpIndicator);
        mHomeAsUpIndicator.setImageDrawable(mDrawerArrowDrawable);
        mHomeAsUpIndicator.setOnClickListener(this);

        mTitleText = mActionBar.findViewById(R.id.text_title);
        mTitleText.post(() -> {
            ViewGroup.MarginLayoutParams hauilp = (ViewGroup.MarginLayoutParams)
                    mHomeAsUpIndicator.getLayoutParams();
            ViewGroup.MarginLayoutParams ttlp = (ViewGroup.MarginLayoutParams)
                    mTitleText.getLayoutParams();
            MarginLayoutParamsCompat.setMarginStart(ttlp,
                    DensityUtils.dp2px(app, 10f) /* margin */
                            + app.getVideoThumbWidth()
                            - hauilp.leftMargin - hauilp.rightMargin
                            - mHomeAsUpIndicator.getWidth() - mTitleText.getWidth());
            mTitleText.setLayoutParams(ttlp);
        });

        mFragmentViewPager = findViewById(R.id.viewpager_fragments);
        mFragmentViewPager.setScrollEnabled(false);
        //noinspection deprecation
        mFragmentViewPager.setAdapter(
                new FragmentPagerAdapter(getSupportFragmentManager(),
                        FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
                    final String[] fragmentTittles = {
                            getString(R.string.localVideos), getString(R.string.onlineVideos)
                    };

                    @NonNull
                    @Override
                    public Fragment getItem(int position) {
                        return mFragments[position];
                    }

                    @Nullable
                    @Override
                    public CharSequence getPageTitle(int position) {
                        return fragmentTittles[position];
                    }

                    @Override
                    public int getCount() {
                        return mFragments.length;
                    }
                });
        ViewPager.OnPageChangeListener onPageChangeListener = new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                //noinspection ConstantConditions,deprecation
                Fragment fragment = ((FragmentPagerAdapter) mFragmentViewPager.getAdapter())
                        .getItem(position);
                if (fragment instanceof LocalVideosFragment) {
                    mActionButton.setId(R.id.btn_search);
                    mActionButton.setImageResource(R.drawable.ic_search);
                    mSlidingDrawerLayout.setContentSensitiveEdgeSize(screenWidth);

                } else if (fragment instanceof YoutubeFragment) {
                    mActionButton.setId(R.id.btn_link);
                    mActionButton.setImageResource(R.drawable.ic_link);
                    mSlidingDrawerLayout.setContentSensitiveEdgeSize(
                            Utils.dp2px(MainActivity.this, SlidingDrawerLayout.DEFAULT_EDGE_SIZE));
                }
            }
        };
        mFragmentViewPager.addOnPageChangeListener(onPageChangeListener);
        onPageChangeListener.onPageSelected(0);
//        mFragmentViewPager.setCurrentItem(0);

        mFragmentTabLayout = findViewById(R.id.tablayout_fragments);
        mFragmentTabLayout.setupWithViewPager(mFragmentViewPager);
    }

    private LocalVideosFragment getLocalVideosFragment() {
        return (LocalVideosFragment) mFragments[INDEX_LOCAL_VIDEOS_FRAGMENT];
    }

    @Synthetic void setLightDrawerStatus(boolean light) {
        mIsDrawerStatusLight = light;
        AppPrefs.getSingleton(this).edit().setLightDrawerStatus(light).apply();
        if (mDrawerScrollPercent >= 0.5f) {
            setLightStatus(light);
        }
    }

    @Synthetic void setDrawerBackground(String path) {
        if (path != null) {
            Executors.SERIAL_EXECUTOR.execute(new LoadDrawerImageTask(this, path));
        }
    }

    private static final class LoadDrawerImageTask implements Runnable {
        final WeakReference<MainActivity> mActivityRef;
        final String mImagePath;

        LoadDrawerImageTask(MainActivity activity, String imagePath) {
            mActivityRef = new WeakReference<>(activity);
            mImagePath = imagePath;
        }

        @Override
        public void run() {
            final MainActivity activity = mActivityRef.get();
            if (activity == null || activity.isFinishing()) {
                return;
            }

            final Bitmap bitmap = BitmapUtils.decodeRotatedBitmapFormFile(mImagePath);
            if (bitmap == null) {
                return;
            }
            final int drawerWidth = activity.mDrawerImage.getWidth();
            final int drawerHeight = activity.mDrawerImage.getHeight();
            final Bitmap centerCroppedBitmap = BitmapUtils.centerCroppedBitmap(
                    bitmap, drawerWidth, drawerHeight, true, true);
            Executors.MAIN_EXECUTOR.post(() -> {
                if (activity.isFinishing() || mImagePath.equals(activity.mDrawerImage.getTag())) {
                    activity.recycleBmpIfNotDrawerImage(centerCroppedBitmap);
                    return;
                }

                activity.recycleDrawerImage();
                activity.mDrawerImage.setImageBitmap(centerCroppedBitmap);
                activity.mDrawerImage.setTag(mImagePath);

                AppPrefs asp = AppPrefs.getSingleton(activity);
                String savedPath = asp.getDrawerBackgroundPath();
                if (mImagePath.equals(savedPath)) {
                    activity.setLightDrawerStatus(asp.isLightDrawerStatus());
                    activity.mDrawerListAdapter.setLightDrawerListForeground(
                            asp.isLightDrawerListForeground());
                } else {
                    asp.setDrawerBackgroundPath(mImagePath);

                    final int defColor = ThemeUtils.isNightMode(activity) ? Color.BLACK : Color.WHITE;
                    final boolean lightBackground = ColorUtils.isLightColor(
                            BitmapUtils.getDominantColor(centerCroppedBitmap, defColor));
                    activity.setLightDrawerStatus(lightBackground);
                    activity.mDrawerListAdapter.setLightDrawerListForeground(!lightBackground);
                }
            });
        }
    }

    @Synthetic void recycleBmpIfNotDrawerImage(Bitmap bitmap) {
        Drawable d = mDrawerImage.getDrawable();
        if (d instanceof BitmapDrawable && ((BitmapDrawable) d).getBitmap() != bitmap) {
            bitmap.recycle();
        }
    }

    @Synthetic void recycleDrawerImage() {
        Drawable d = mDrawerImage.getDrawable();
        if (d instanceof BitmapDrawable) {
            ((BitmapDrawable) d).getBitmap().recycle();
        }
    }

    @Override
    public void onBackPressed() {
        if (mSlidingDrawerLayout.hasOpenedDrawer()) {
            mSlidingDrawerLayout.closeDrawer(true);
            return;
        }

        if (((OnBackPressedListener) mFragments[mFragmentViewPager.getCurrentItem()]).onBackPressed()) {
            return;
        }

        if (!mIsBackPressed) {
            mIsBackPressed = true;
            mSlidingDrawerLayout.postDelayed(() -> mIsBackPressed = false, 1500);
            UiUtils.showUserCancelableSnackbar(mSlidingDrawerLayout,
                    R.string.pressAgainToExitApp, Snackbar.LENGTH_SHORT);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this$ = null;
        if (mDrawerList != null) {
            recycleDrawerImage();
        }
        if (mOnCheckUpdateResultListener != null) {
            MergeAppUpdateChecker.getSingleton(this).removeOnResultListener(mOnCheckUpdateResultListener);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //noinspection SwitchStatementWithTooFewBranches
        switch (requestCode) {
            case REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE:
                if (data != null && data.getData() != null) {
                    setDrawerBackground(FileUtils.UriResolver.getPath(this, data.getData()));
                }
                break;
//            case REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION:
//                checkUpdateIfPermissionGranted(true);
//                break;
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_homeAsUpIndicator:
                if (mSlidingDrawerLayout.hasOpenedDrawer()) {
                    mSlidingDrawerLayout.closeDrawer(true);
                } else {
                    mSlidingDrawerLayout.openDrawer(Gravity.START, true);
                }
                break;

            case R.id.btn_search:
                mToolbarActions.goToLocalSearchedVideosFragment(getLocalVideosFragment());
                break;
            case R.id.btn_link:
                mToolbarActions.showOpenVideoLinkDialog(v.getContext());
                break;

            case R.id.btn_ok_aboutAppDialog:
            case R.id.btn_ok_updateLogsDialog:
                ((Dialog) v.getTag()).cancel();
                break;
        }
    }

    @Synthetic static final int[] sDrawerListItemIDs = {
            R.string.checkForUpdates,
            R.string.aboutApp,
            R.string.updateLogs,
            R.string.userFeedback,
            R.string.drawerSettings,
            R.string.darkTheme,
    };

    private final class DrawerListAdapter extends BaseAdapter2 {

        final String[] mDrawerListItems;

        final Context mContext = MainActivity.this;

        @ColorInt
        int mTextColor;
        @ColorInt
        int mSubTextColor;
        @ColorInt
        static final int SUBTEXT_HIGHLIGHT_COLOR = Color.RED;

        final Drawable mForwardDrawable;
//        final Drawable mLightDrawerListDivider = ContextCompat.getDrawable(
//                mContext, R.drawable.divider_light_drawer_list);
//        final Drawable mDarkDrawerListDivider = ContextCompat.getDrawable(
//                mContext, R.drawable.divider_dark_drawer_list);

        DrawerListAdapter() {
            mDrawerListItems = new String[sDrawerListItemIDs.length];
            for (int i = 0; i < mDrawerListItems.length; i++) {
                mDrawerListItems[i] = getString(sDrawerListItemIDs[i]);
            }

            Drawable temp = ContextCompat.getDrawable(mContext, R.drawable.ic_forward);
            //noinspection ConstantConditions
            mForwardDrawable = DrawableCompat.wrap(temp);
            DrawableCompat.setTintList(mForwardDrawable, null);

            final boolean nightMode = ThemeUtils.isNightMode(mContext);
            mIsDrawerStatusLight = !nightMode;
            mIsDrawerListForegroundLight = !nightMode;
            applyDrawerForeground(nightMode);
        }

        void setLightDrawerListForeground(boolean light) {
            AppPrefs.getSingleton(mContext).edit().setLightDrawerListForeground(light).apply();
            applyDrawerForeground(light);
        }

        void applyDrawerForeground(boolean light) {
            if (mIsDrawerListForegroundLight != light) {
                mIsDrawerListForegroundLight = light;

                if (light) {
                    mTextColor = TEXT_COLOR_PRIMARY_LIGHT;
                    mSubTextColor = 0xFFE0E0E0;
                    DrawableCompat.setTint(mForwardDrawable, Color.LTGRAY);
                } else {
                    mTextColor = TEXT_COLOR_PRIMARY_DARK;
                    mSubTextColor = Color.GRAY;
                    // 清除tint
                    DrawableCompat.setTintList(mForwardDrawable, null);
                }
                notifyDataSetChanged();

//                if (light) {
//                    mDrawerList.setDivider(mLightDrawerListDivider);
//                } else {
//                    mDrawerList.setDivider(mDarkDrawerListDivider);
//                }
            }
        }

        @Override
        public int getCount() {
            return mDrawerListItems.length;
        }

        @Override
        public String getItem(int position) {
            return mDrawerListItems[position];
        }

        @Override
        public long getItemId(int position) {
            if (position == 0) {
                final int baseId = sDrawerListItemIDs[0];
                if (!TextUtils.isEmpty(mCheckUpdateResultText)) {
                    return 31L * baseId + mCheckUpdateResultText.hashCode();
                }
                return baseId;
            }
            return sDrawerListItemIDs[position];
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup listview) {
            ViewHolder vh;
            if (convertView == null) {
                vh = new ViewHolder(listview);
                convertView = vh.itemView;
                convertView.setTag(vh);
            } else {
                vh = (ViewHolder) convertView.getTag();
            }
            vh.text.setText(mDrawerListItems[position]);
            vh.text.setTextColor(mTextColor);
            if (position == 0 && !TextUtils.isEmpty(mCheckUpdateResultText)) {
                vh.subText.setText(mCheckUpdateResultText);
                vh.subText.setTextColor(mFindNewVersion.equals(mCheckUpdateResultText) ?
                        SUBTEXT_HIGHLIGHT_COLOR : mSubTextColor);
                vh.subText.setCompoundDrawables(null, null, null, null);
            } else {
                vh.subText.setText(EMPTY_STRING);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    vh.subText.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            null, null, mForwardDrawable, null);
                } else {
                    vh.subText.setCompoundDrawablesWithIntrinsicBounds(
                            null, null, mForwardDrawable, null);
                }
            }
            return super.getView(position, convertView, listview);
        }

        final class ViewHolder {
            final View itemView;
            final TextView text;
            final TextView subText;

            ViewHolder(ViewGroup adapterView) {
                itemView = LayoutInflater.from(adapterView.getContext())
                        .inflate(R.layout.item_drawer_list, adapterView, false);
                text = itemView.findViewById(R.id.text_list);
                subText = itemView.findViewById(R.id.subtext_list);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> listview, View view, int position, long id) {
        switch (sDrawerListItemIDs[position]) {
            case R.string.checkForUpdates:
                checkUpdate(true);
                break;
            case R.string.aboutApp:
                showAboutAppDialog();
                break;
            case R.string.updateLogs:
                showUpdateLogsDialog();
                break;
            case R.string.userFeedback:
                startActivity(new Intent(this, FeedbackActivity.class));
                break;
            case R.string.drawerSettings:
                showDrawerSettingsMenu(view);
                break;
            case R.string.darkTheme:
                showThemePicker(view);
                break;
        }
    }

    private void checkUpdate(boolean toastResult) {
//        if (!FloatingWindowPermissionUtils.hasPermission(this)) {
//            FloatingWindowPermissionUtils.applyForPermission(
//                    this, REQUEST_CODE_APPLY_FOR_FLOATING_WINDOW_PERMISSION);
//            return;
//        }

        baseCheckUpdate(toastResult);
    }

//    private void checkUpdateIfPermissionGranted(boolean toastResult) {
//        if (FloatingWindowPermissionUtils.hasPermission(this)) {
//            baseCheckUpdate(toastResult);
//        }
//    }

    private void baseCheckUpdate(boolean toastResult) {
        MergeAppUpdateChecker auc = MergeAppUpdateChecker.getSingleton(this);
        if (mOnCheckUpdateResultListener == null) {
            mOnCheckUpdateResultListener = findNewVersion -> {
                mCheckUpdateResultText = findNewVersion ? mFindNewVersion : mIsTheLatestVersion;
                if (mDrawerListAdapter != null) {
                    mDrawerListAdapter.notifyItemChanged(0);
                }
            };
            auc.addOnResultListener(mOnCheckUpdateResultListener);
        }
        auc.checkUpdate(toastResult);
    }

    private void showAboutAppDialog() {
        View view = View.inflate(this, R.layout.dialog_about_app, null);
        TextView button = view.findViewById(R.id.btn_ok_aboutAppDialog);

        Dialog dialog = new AppCompatDialog(this, R.style.DialogStyle_NoTitle);
        dialog.setContentView(view);
        dialog.show();

        button.setOnClickListener(this);
        button.setTag(dialog);
    }

    private void showUpdateLogsDialog() {
        final MainActivity _this = this;
        final View view = View.inflate(_this, R.layout.dialog_update_logs, null);
        final ScrollView scrollView = view.findViewById(R.id.scrollview);
        final TextView tv = view.findViewById(R.id.text_updateLogs);

        String text = null;
        try {
            text = IOUtils.decodeStringFromStream(getAssets().open("updateLogs.txt"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (text == null) return;
        tv.setText(text);

        tv.post(() -> {
            TextViewUtils.setHangingIndents(tv, 4);

            final String newText = tv.getText().toString();
            final SpannableString ss = new SpannableString(newText);

            final String start = getString(R.string.appName_chinese) + "v";
            final String end = getString(R.string.updateAppendedColon);
            for (int i = 0, count = BuildConfig.VERSION_CODE - 1, fromIndex = 0; i < count; i++) {
                final int startIndex = newText.indexOf(start, fromIndex);
                final int endIndex = newText.indexOf(end, startIndex) + end.length();
                ss.setSpan(new TextAppearanceSpan(_this, R.style.TextAppearance_UpdateLogTitle),
                        startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                fromIndex = endIndex;
            }

            tv.setText(ss);

            tv.post(() -> scrollView.smoothScrollTo(0, tv.getHeight() - scrollView.getHeight()));
        });

        Dialog dialog = new AppCompatDialog(_this, R.style.DialogStyle_MinWidth_NoTitle);
        dialog.setContentView(view);
        dialog.show();

        View button = view.findViewById(R.id.btn_ok_updateLogsDialog);
        button.setOnClickListener(_this);
        button.setTag(dialog);
    }

    private void showDrawerSettingsMenu(View anchor) {
        PopupMenu ppm = new PopupMenu(this, anchor);

        Menu menu = ppm.getMenu();
        menu.add(Menu.NONE, R.id.setBackground, Menu.NONE, R.string.setBackground);
        SubMenu subMenu = menu.addSubMenu(Menu.NONE, R.id.setForeground, Menu.NONE, R.string.setForeground);

        subMenu.add(R.id.setForeground, R.id.changeTextColor, Menu.NONE,
                mIsDrawerListForegroundLight ? R.string.setDarkTexts : R.string.setLightTexts);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT) {
            subMenu.add(R.id.setForeground, R.id.changeStatusTextColor, Menu.NONE,
                    mIsDrawerStatusLight ? R.string.setLightStatus : R.string.setDarkStatus);
        }

        ppm.setGravity(Gravity.END);
        ppm.show();
        ppm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.setBackground:
                    Intent it = new Intent(Intent.ACTION_GET_CONTENT).setType("image/*");
                    startActivityForResult(it, REQUEST_CODE_CHOSE_DRAWER_BACKGROUND_PICTURE);
                    return true;
                case R.id.changeTextColor:
                    mDrawerListAdapter.setLightDrawerListForeground(!mIsDrawerListForegroundLight);
                    return true;
                case R.id.changeStatusTextColor:
                    setLightDrawerStatus(!mIsDrawerStatusLight);
                    return true;
                default:
                    return false;
            }
        });
    }

    private void showThemePicker(View anchor) {
        PopupMenu ppm = new PopupMenu(this, anchor);
        Menu menu = ppm.getMenu();
        int nightMode = AppCompatDelegate.getDefaultNightMode();
        if (nightMode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            menu.add(Menu.NONE, R.id.followsSystem, Menu.NONE, R.string.followsSystem);
        }
        if (nightMode != AppCompatDelegate.MODE_NIGHT_NO) {
            menu.add(Menu.NONE, R.id.turnOff, Menu.NONE, R.string.turnOff);
        }
        if (nightMode != AppCompatDelegate.MODE_NIGHT_YES) {
            menu.add(Menu.NONE, R.id.turnOn, Menu.NONE, R.string.turnOn);
        }
        ppm.setGravity(Gravity.END);
        ppm.show();
        ppm.setOnMenuItemClickListener(item -> {
            int mode;
            switch (item.getItemId()) {
                case R.id.followsSystem:
                    mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                    break;
                case R.id.turnOff:
                    mode = AppCompatDelegate.MODE_NIGHT_NO;
                    break;
                case R.id.turnOn:
                    mode = AppCompatDelegate.MODE_NIGHT_YES;
                    break;
                default:
                    return false;
            }
            AppPrefs.getSingleton(this).edit().setDefaultNightMode(mode).apply();
            AppCompatDelegate.setDefaultNightMode(mode);
            // Apply default night mode for web process...
            WebService.bind(this, webService -> {
                try {
                    webService.applyDefaultNightMode(mode);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
            return true;
        });
    }

    @Override
    public void onDrawerOpened(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
    }

    @Override
    public void onDrawerClosed(@NonNull SlidingDrawerLayout parent, @NonNull View drawer) {
    }

    @Override
    public void onScrollPercentChange(
            @NonNull SlidingDrawerLayout parent, @NonNull View drawer, float percent) {
        mDrawerArrowDrawable.setProgress(percent);

        final boolean light = percent >= 0.5f;
        if ((light && mDrawerScrollPercent < 0.5f || !light && mDrawerScrollPercent >= 0.5f)
                && AppPrefs.getSingleton(this).isLightDrawerStatus()) {
            setLightStatus(light);
        }
        mDrawerScrollPercent = percent;
    }

    @Override
    public void onScrollStateChange(
            @NonNull SlidingDrawerLayout parent, @NonNull View drawer, int state) {
        switch (state) {
            case SlidingDrawerLayout.SCROLL_STATE_TOUCH_SCROLL:
            case SlidingDrawerLayout.SCROLL_STATE_AUTO_SCROLL:
                mTitleText.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mActionButton.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                mFragmentTabLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                break;
            case SlidingDrawerLayout.SCROLL_STATE_IDLE:
                mTitleText.setLayerType(View.LAYER_TYPE_NONE, null);
                mActionButton.setLayerType(View.LAYER_TYPE_NONE, null);
                mFragmentTabLayout.setLayerType(View.LAYER_TYPE_NONE, null);
                break;
        }
    }

    @Override
    public void setLightStatus(boolean light) {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SystemBarUtils.setLightStatus(window, light);
            // MIUI6...
        } else if (OSHelper.getMiuiVersion() >= 6) {
            SystemBarUtils.setLightStatusForMIUI(window, light);
            // FlyMe4...
        } else if (OSHelper.isFlyme4OrLater()) {
            SystemBarUtils.setLightStatusForFlyme(window, light);

        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SystemBarUtils.setTranslucentStatus(window, light);
        }
    }

    @Override
    public void setSideDrawerEnabled(boolean enabled) {
        mSlidingDrawerLayout.setDrawerEnabledInTouch(Gravity.START, enabled);
    }

    @Override
    public void goToLocalFoldedVideosFragment(@NonNull Bundle args) {
        getLocalVideosFragment().goToLocalFoldedVideosFragment(args);
    }

    @Override
    public boolean isRefreshLayoutEnabled() {
        return getLocalVideosFragment().isRefreshLayoutEnabled();
    }

    @Override
    public void setRefreshLayoutEnabled(boolean enabled) {
        getLocalVideosFragment().setRefreshLayoutEnabled(enabled);
    }

    @Override
    public boolean isRefreshLayoutRefreshing() {
        return getLocalVideosFragment().isRefreshLayoutRefreshing();
    }

    @Override
    public void setRefreshLayoutRefreshing(boolean refreshing) {
        getLocalVideosFragment().setRefreshLayoutRefreshing(refreshing);
    }

    @Override
    public void setOnRefreshLayoutChildScrollUpCallback(@Nullable SwipeRefreshLayout.OnChildScrollUpCallback callback) {
        getLocalVideosFragment().setOnRefreshLayoutChildScrollUpCallback(callback);
    }

    @NonNull
    @Override
    public ViewGroup getActionBar(boolean tmp) {
        return tmp ? mTmpActionBar : mActionBar;
    }

    @Override
    public void showActionBar(boolean show) {
        mActionBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setActionBarAlpha(float alpha) {
        mActionBar.setAlpha(alpha);
    }

    @Override
    public void setTmpActionBarAlpha(float alpha) {
        mTmpActionBar.setAlpha(alpha);
    }

    private void insertTopPaddingToActionBarIfNeeded(View actionbar) {
        if (isLayoutUnderStatusBar()) {
            final int statusHeight = App.getInstance(this).getStatusHeightInPortrait();
            switch (actionbar.getLayoutParams().height) {
                case ViewGroup.LayoutParams.WRAP_CONTENT:
                case ViewGroup.LayoutParams.MATCH_PARENT:
                    break;
                default:
                    actionbar.getLayoutParams().height += statusHeight;
            }
            actionbar.setPadding(
                    actionbar.getPaddingLeft(),
                    actionbar.getPaddingTop() + statusHeight,
                    actionbar.getPaddingRight(),
                    actionbar.getPaddingBottom());
        }
    }

    private boolean isLayoutUnderStatusBar() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    @Override
    public void showTabItems(boolean show) {
        mFragmentTabLayout.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void setTabItemsEnabled(boolean enabled) {
        UiUtils.setTabItemsEnabled(mFragmentTabLayout, enabled);
    }

    @Override
    public void onLocalSearchedVideosFragmentAttached() {
        mTmpActionBar = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.actionbar_local_searched_videos_fragment, mActionBarContainer, false);
        if (isLayoutUnderStatusBar()) {
            UiUtils.setViewMargins(mTmpActionBar,
                    0, App.getInstance(this).getStatusHeightInPortrait(), 0, 0);
        }
        mActionBarContainer.addView(mTmpActionBar);
    }

    @Override
    public void onLocalSearchedVideosFragmentDetached() {
        mActionBarContainer.removeView(mTmpActionBar);
        mTmpActionBar = null;
    }

    @Override
    public void onLocalFoldedVideosFragmentAttached() {
        mActionBar.setVisibility(View.GONE);
        mTmpActionBar = (ViewGroup) LayoutInflater.from(this)
                .inflate(R.layout.actionbar_local_folded_videos_fragment, mActionBarContainer, false);
        mActionBarContainer.addView(mTmpActionBar);
        insertTopPaddingToActionBarIfNeeded(mTmpActionBar);
    }

    @Override
    public void onLocalFoldedVideosFragmentDetached() {
//        mActionBar.setVisibility(View.VISIBLE)
        mActionBarContainer.removeView(mTmpActionBar);
        mTmpActionBar = null;
    }
}
