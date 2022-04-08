/*
 * Created on 2022-2-23 4:10:58 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.MessageQueue;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.liuzhenlin.common.compat.LooperCompat;
import com.liuzhenlin.common.listener.OnBackPressedListener;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.NetworkUtil;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.view.SwipeRefreshLayout;
import com.liuzhenlin.videos.web.BuildConfig;
import com.liuzhenlin.videos.web.R;
import com.liuzhenlin.videos.web.player.Constants;

public class YoutubeFragment extends Fragment implements View.OnClickListener, OnBackPressedListener {

    private Context mContext;

    @Synthetic WebView mYoutubeView;
    @Synthetic String mUrl = Constants.URL_BLANK;
    private final MessageQueue.IdleHandler mCheckCurrentUrlIdleHandler = () -> {
        WebView view = mYoutubeView;
        String url = view.getUrl();
        if (!url.equals(mUrl)) {
            if (YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(view.getContext(), url)) {
                view.goBack();
            }
            mUrl = url;
        }
        return true;
    };
    private boolean mIdleHandlerAdded;

    private boolean mBackPressed;
    private boolean mExitOnBackPressed;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_youtube, container, false);

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(
                () -> {
                    if (mYoutubeView == null) {
                        if (NetworkUtil.isNetworkConnected(mContext)) {
                            recreateHost();
                        }
                    } else {
                        mYoutubeView.reload();
                    }
                });
        swipeRefreshLayout.setOnChildScrollUpCallback(
                (parent, child) -> mYoutubeView != null && mYoutubeView.getScrollY() > 0);

        ViewStub viewStub = view.findViewById(R.id.viewStub);
        if (NetworkUtil.isNetworkConnected(mContext)) {
            mExitOnBackPressed = false;
            viewStub.setLayoutResource(R.layout.web_content_youtube_fragment);
            viewStub.inflate();

            // Assigning real view object to mTarget in SwipeRefreshLayout is impossible
            // when ViewStub was not inflated.
            setSwipeRefreshColorSchemeResources(swipeRefreshLayout);
            swipeRefreshLayout.setOnRequestDisallowInterceptTouchEventCallback(() -> true);

            mYoutubeView = view.findViewById(R.id.web_youtube);
            mYoutubeView.getSettings().setJavaScriptEnabled(true);
            mYoutubeView.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageStarted(WebView view, String url, Bitmap favicon) {
                    super.onPageStarted(view, url, favicon);
                    Executors.MAIN_EXECUTOR.execute(() -> swipeRefreshLayout.setRefreshing(true));
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Executors.MAIN_EXECUTOR.execute(() -> swipeRefreshLayout.setRefreshing(false));
                }
            });
            mYoutubeView.loadUrl(Youtube.URLs.HOME);
        } else {
            mExitOnBackPressed = BuildConfig.BUILD_AS_APP;
            viewStub.setLayoutResource(R.layout.layout_no_internet);
            viewStub.inflate();

            // Assigning real view object to mTarget in SwipeRefreshLayout is impossible
            // when ViewStub was not inflated.
            setSwipeRefreshColorSchemeResources(swipeRefreshLayout);

            view.findViewById(R.id.btn_retry).setOnClickListener(this);
            view.findViewById(R.id.btn_settings).setOnClickListener(this);
            view.findViewById(R.id.btn_exit).setOnClickListener(this);
        }
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mYoutubeView != null && !mIdleHandlerAdded) {
            mIdleHandlerAdded = true;
            MessageQueue msgQueue = LooperCompat.getQueue(Executors.MAIN_EXECUTOR.getLooper());
            if (msgQueue != null) {
                msgQueue.addIdleHandler(mCheckCurrentUrlIdleHandler);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mIdleHandlerAdded) {
            mIdleHandlerAdded = false;
            MessageQueue msgQueue = LooperCompat.getQueue(Executors.MAIN_EXECUTOR.getLooper());
            if (msgQueue != null) {
                msgQueue.removeIdleHandler(mCheckCurrentUrlIdleHandler);
            }
        }
    }

    private void setSwipeRefreshColorSchemeResources(SwipeRefreshLayout srl) {
        srl.setColorSchemeResources(R.color.pink, R.color.lightBlue, R.color.purple);
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();
        if (viewId == R.id.btn_retry) {
            recreateHost();
        } else if (viewId == R.id.btn_settings) {
            startActivityForResult(new Intent(Settings.ACTION_WIRELESS_SETTINGS), 0);
        } else if (viewId == R.id.btn_exit) {
            finishHost();
        }
    }

    private void recreateHost() {
        Activity host = getActivity();
        if (host != null) {
            ActivityCompat.recreate(host);
        }
    }

    private void finishHost() {
        Activity host = getActivity();
        if (host != null) {
            host.finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            if (NetworkUtil.isNetworkConnected(mContext)) {
                recreateHost();
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (mExitOnBackPressed) {
            return false;
        }

        if (mYoutubeView.canGoBack()) {
            mYoutubeView.goBack();
        } else {
            if (!BuildConfig.BUILD_AS_APP) {
                return false;
            }

            if (mBackPressed) {
                return false;
            }
            mBackPressed = true;
            Toast.makeText(mContext, R.string.pressAgainToExit, Toast.LENGTH_SHORT).show();
            Executors.MAIN_EXECUTOR.getHandler().postDelayed(() -> mBackPressed = false, 2000);
        }
        return true;
    }
}
