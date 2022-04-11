/*
 * Created on 2019/11/3 2:23 PM.
 * Copyright © 2019–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.util.ObjectsCompat;
import androidx.preference.PreferenceManager;

import com.bumptech.glide.util.Synthetic;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuzhenlin.common.utils.ActivityUtils;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.NotificationChannelManager;
import com.liuzhenlin.common.utils.Singleton;
import com.liuzhenlin.common.utils.TextViewUtils;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.Configs;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.Prefs;
import com.liuzhenlin.videos.R;

import org.apache.http.conn.ConnectTimeoutException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.liuzhenlin.common.utils.Utils.hasNotification;
import static com.liuzhenlin.common.utils.Utils.postTillConditionMeets;

/**
 * @author 刘振林
 */
@MainThread
public final class AppUpdateChecker {

    public interface OnResultListener {
        void onResult(boolean findNewVersion);
    }

    private static final String TAG = "AppUpdateChecker";

    private static final int TIMEOUT_CONNECTION = 10 * 1000; // ms
    private static final int TIMEOUT_READ = 30 * 1000; // ms

    private static final String LINK_APP_INFOS =
            "https://gitlab.com/lzls/Videos-Server/-/raw/master/app/Android/app.json";

    @Synthetic String mAppName;
    @Synthetic String mVersionName;
    @Synthetic String mAppLink;
    @Synthetic String mAppSha1;
    @Synthetic StringBuilder mUpdateLog;
    @Synthetic String mPromptDialogAnchorActivityClsName;

    @Synthetic final Context mContext;
    @Synthetic final H mH;
    @Synthetic boolean mToastResult;
    private boolean mCheckInProgress;

    @Synthetic List<OnResultListener> mListeners;

    private static final String EXTRA_APP_NAME = "appName";
    private static final String EXTRA_VERSION_NAME = "versionName";
    private static final String EXTRA_APP_LINK = "appLink";
    private static final String EXTRA_APP_SHA1 = "appSha1";
    @Synthetic Intent mServiceIntent;

    private static final Singleton<Context, AppUpdateChecker> sAppUpdateCheckerSingleton =
            new Singleton<Context, AppUpdateChecker>() {
                @SuppressLint("SyntheticAccessor")
                @NonNull
                @Override
                protected AppUpdateChecker onCreate(Context... ctxs) {
                    return new AppUpdateChecker(ctxs[0]);
                }
            };

    @NonNull
    public static AppUpdateChecker getSingleton(@NonNull Context context) {
        return sAppUpdateCheckerSingleton.get(context);
    }

    private AppUpdateChecker(Context context) {
        mContext = context.getApplicationContext();
        mH = new H();
    }

    @Synthetic boolean hasOnResultListener() {
        return mListeners != null && !mListeners.isEmpty();
    }

    // 注：添加后请记得移除，以免引起内存泄漏
    public void addOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = new ArrayList<>(1);
            }
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
    }

    public void removeOnResultListener(@Nullable OnResultListener listener) {
        if (listener != null && hasOnResultListener()) {
            mListeners.remove(listener);
        }
    }

    public void checkUpdate() {
        checkUpdate(true);
    }

    @SuppressLint("StaticFieldLeak")
    public void checkUpdate(boolean toastResult) {
        mToastResult = toastResult;

        if (mCheckInProgress) return;
        mCheckInProgress = true;
        new AsyncTask<Void, Void, Integer>() {
            static final int RESULT_FIND_NEW_VERSION = 1;
            static final int RESULT_NO_NEW_VERSION = 2;
            static final int RESULT_CONNECTION_TIMEOUT = 3;
            static final int RESULT_READ_TIMEOUT = 4;

            @Override
            protected Integer doInBackground(Void... voids) {
                String json;

                HttpURLConnection conn = null;
                try {
                    URL url = new URL(LINK_APP_INFOS);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    json = IOUtils.decodeStringFromStream(conn.getInputStream());

                    // 连接服务器超时
                } catch (ConnectTimeoutException e) {
                    return RESULT_CONNECTION_TIMEOUT;

                    // 读取数据超时
                } catch (SocketTimeoutException e) {
                    return RESULT_READ_TIMEOUT;

                } catch (IOException e) {
                    e.printStackTrace();
                    return 0;

                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }

                //noinspection ConstantConditions
                JsonObject appInfos = JsonParser.parseString(json).getAsJsonObject()
                        .get("appInfos").getAsJsonObject();

                mAppName = appInfos.get("appName").getAsString();
                mPromptDialogAnchorActivityClsName =
                        appInfos.get("promptDialogAnchorActivityClsName").getAsString();

                boolean findNewVersion = Configs.DEBUG_APP_UPDATE;
                String updateChannel =
                        PreferenceManager.getDefaultSharedPreferences(mContext)
                                .getString(Prefs.KEY_UPDATE_CHANNEL, Prefs.UPDATE_CHANNEL_STABLE);
                switch (updateChannel) {
                    case Prefs.UPDATE_CHANNEL_STABLE:
                        findNewVersion |=
                                appInfos.get("versionCode").getAsInt() > BuildConfig.VERSION_CODE;
                        break;
                    case Prefs.UPDATE_CHANNEL_BETA:
                        appInfos = appInfos.get("beta").getAsJsonObject();
                        findNewVersion |=
                                appInfos.get("versionCode").getAsInt() > BuildConfig.BETA_VERSION_CODE;
                        break;
                    case Prefs.UPDATE_CHANNEL_DEV:
                        appInfos = appInfos.get("dev").getAsJsonObject();
                        findNewVersion |=
                                appInfos.get("versionCode").getAsInt() > BuildConfig.DEV_VERSION_CODE;
                        break;
                }

                // 检测到版本更新
                if (findNewVersion) {
                    mAppLink = appInfos.get("appLink").getAsString();
                    mAppSha1 = appInfos.get("appSha1").getAsString();
                    mVersionName = appInfos.get("versionName").getAsString();
                    mUpdateLog = new StringBuilder();
                    for (JsonElement log : appInfos.get("updateLogs").getAsJsonArray()) {
                        mUpdateLog.append(log.getAsString()).append("\n");
                    }
                    mUpdateLog.deleteCharAt(mUpdateLog.length() - 1);
                }
                return findNewVersion ? RESULT_FIND_NEW_VERSION : RESULT_NO_NEW_VERSION;
            }

            @Override
            protected void onPostExecute(Integer result) {
                switch (result) {
                    case RESULT_FIND_NEW_VERSION:
                        mH.sendEmptyMessage(H.MSG_FIND_NEW_VERSION);
                        showUpdatePromptDialog();
                        break;
                    case RESULT_NO_NEW_VERSION:
                        mH.sendEmptyMessage(H.MSG_NO_NEW_VERSION);
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.isTheLatestVersion, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_CONNECTION_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                        }
                        reset();
                        break;
                    case RESULT_READ_TIMEOUT:
                        if (mToastResult) {
                            Toast.makeText(mContext,
                                    R.string.readTimeout, Toast.LENGTH_SHORT).show();
                        }
                    default:
                        reset();
                        break;
                }
            }
        }.executeOnExecutor(Executors.THREAD_POOL_EXECUTOR);
    }

    /**
     * 弹出对话框，提醒用户更新
     */
    @Synthetic void showUpdatePromptDialog() {
        Activity anchorActivity = ActivityUtils.getActivityForName(mPromptDialogAnchorActivityClsName);
        if (anchorActivity == null || anchorActivity.isFinishing()) {
            Log.w(TAG, "The Activity in which the dialog should run doesn't exist, " +
                    "so it will not be showed at all and this check finishes.");
            reset();
            return;
        }

        Dialog dialog = new AppCompatDialog(anchorActivity, R.style.DialogStyle_MinWidth_NoTitle);

        View view = View.inflate(anchorActivity, R.layout.dialog_app_update_prompt, null);

        view.<TextView>findViewById(R.id.text_updateLogTitle)
                .setText(mContext.getString(R.string.updateLog, mAppName, mVersionName));

        TextView tv = view.findViewById(R.id.text_updateLog);
        tv.setText(mUpdateLog);
        tv.post(() -> TextViewUtils.setHangingIndents(tv, 4));

        View.OnClickListener listener = v -> {
            switch (v.getId()) {
                // 当点确定按钮时从服务器上下载新的apk，然后安装
                case R.id.btn_confirm:
                    dialog.cancel();
                    if (FileUtils.isExternalStorageMounted()) {
                        mServiceIntent = new Intent(mContext, UpdateAppService.class)
                                .putExtra(EXTRA_APP_NAME, mAppName)
                                .putExtra(EXTRA_VERSION_NAME, mVersionName)
                                .putExtra(EXTRA_APP_LINK, mAppLink)
                                .putExtra(EXTRA_APP_SHA1, mAppSha1);
                        mContext.startService(mServiceIntent);
                    } else {
                        reset();
                        Toast.makeText(mContext, R.string.pleaseInsertSdCardOnYourDeviceFirst,
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
                // 当点取消按钮时不做任何举动
                case R.id.btn_cancel:
                    dialog.cancel();
                    reset();
                    break;
            }
        };
        view.findViewById(R.id.btn_cancel).setOnClickListener(listener);
        view.findViewById(R.id.btn_confirm).setOnClickListener(listener);

//        dialog.getWindow().setType(
//                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
//                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
//                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.setContentView(view);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    @Synthetic void reset() {
        mAppName = null;
        mVersionName = null;
        mAppLink = null;
        mUpdateLog = null;
        mPromptDialogAnchorActivityClsName = null;
        mCheckInProgress = false;
        mServiceIntent = null;
    }

    @SuppressLint("HandlerLeak")
    private final class H extends Handler {
        static final int MSG_STOP_UPDATE_APP_SERVICE = -1;
        static final int MSG_NO_NEW_VERSION = 0;
        static final int MSG_FIND_NEW_VERSION = 1;

        H() {
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            final int what = msg.what;
            switch (what) {
                case MSG_STOP_UPDATE_APP_SERVICE:
                    if (mServiceIntent != null) {
                        mContext.stopService(mServiceIntent);
                        reset();
                    }
                    break;
                case MSG_NO_NEW_VERSION:
                case MSG_FIND_NEW_VERSION:
                    if (hasOnResultListener()) {
                        for (int i = mListeners.size() - 1; i >= 0; i--) {
                            mListeners.get(i).onResult(what != MSG_NO_NEW_VERSION);
                        }
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    }

    @VisibleForTesting
    public static final class UpdateAppService extends Service {
        private static final int INDEX_APP_NAME = 0;
        private static final int INDEX_VERSION_NAME = 1;
        private static final int INDEX_APP_LINK = 2;
        private static final int INDEX_APP_SHA1 = 3;

        @Synthetic volatile UpdateAppTask mTask;

        private CancelAppUpdateReceiver mReceiver;

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            mTask = (UpdateAppTask) new UpdateAppTask(this)
                    .executeOnExecutor(Executors.THREAD_POOL_EXECUTOR,
                            intent.getStringExtra(AppUpdateChecker.EXTRA_APP_NAME),
                            intent.getStringExtra(AppUpdateChecker.EXTRA_VERSION_NAME),
                            intent.getStringExtra(AppUpdateChecker.EXTRA_APP_LINK),
                            intent.getStringExtra(AppUpdateChecker.EXTRA_APP_SHA1));

            mReceiver = new CancelAppUpdateReceiver();
            registerReceiver(mReceiver, new IntentFilter(CancelAppUpdateReceiver.ACTION));

            return START_REDELIVER_INTENT;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (Configs.DEBUG_APP_UPDATE) {
                Log.d(TAG, "Service is destroyed.");
            }

            UpdateAppTask task = mTask;
            if (task != null) {
                task.cancels(true);
            }

            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        private static final class CancelAppUpdateReceiver extends BroadcastReceiver {
            static final String ACTION = CancelAppUpdateReceiver.class.getName();

            CancelAppUpdateReceiver() {
            }

            @Override
            public void onReceive(Context context, Intent intent) {
                if (Configs.DEBUG_APP_UPDATE) {
                    Log.d(TAG, "User cancels updating app and service is going to be stopped...");
                }
                AppUpdateChecker.getSingleton(context)
                        .mH.sendEmptyMessage(H.MSG_STOP_UPDATE_APP_SERVICE);
            }
        }

        private static final class UpdateAppTask extends AsyncTask<String, Void, Void> {
            @SuppressLint("StaticFieldLeak")
            final UpdateAppService mService;

            @SuppressLint("StaticFieldLeak")
            final Context mContext;
            final String mPkgName;

            final NotificationManager mNotificationManager;
            final NotificationCompat.Builder mNotificationBuilder;
            static final int ID_NOTIFICATION = 20191103;

            static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
            // We want at least 2 threads and at most 4 threads to download the new apk,
            // preferring to have 1 less than the CPU count to avoid saturating the CPU
            // with background work
            static final int COUNT_DOWNLOAD_APP_TASK = Math.max(2, Math.min(CPU_COUNT - 1, 4));

            List<DownloadAppTask> mDownloadAppTasks;
            String mAppLink;
            File mApk;
            int mApkLength = -1;
            final AtomicInteger mProgress = new AtomicInteger();

            UpdateAppTask(UpdateAppService service) {
                mService = service;
                mContext = service.getApplicationContext();
                mPkgName = service.getPackageName();

                mNotificationManager = (NotificationManager)
                        mContext.getSystemService(Context.NOTIFICATION_SERVICE);
                String channelId = NotificationChannelManager.getDownloadNotificationChannelId(mContext);
                RemoteViews nv = createNotificationView();
                mNotificationBuilder = new NotificationCompat.Builder(mContext, channelId)
                        .setSmallIcon(R.drawable.ic_media_app_notification)
                        .setTicker(mContext.getString(R.string.downloadingUpdates))
                        .setCustomContentView(nv)
                        .setCustomBigContentView(nv)
                        .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                        .setColor(Consts.COLOR_ACCENT)
                        .setDefaults(0)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                        .setVisibility(NotificationCompat.VISIBILITY_SECRET);
            }

            @SuppressLint("UnspecifiedImmutableFlag")
            @Synthetic RemoteViews createNotificationView() {
                RemoteViews nv = new RemoteViews(mPkgName, R.layout.notification_download_app);
                nv.setOnClickPendingIntent(R.id.btn_cancel_danv,
                        PendingIntent.getBroadcast(
                                mContext,
                                0,
                                new Intent(CancelAppUpdateReceiver.ACTION),
                                0));
                return nv;
            }

            @Override
            protected void onPreExecute() {
                mService.startForeground(ID_NOTIFICATION, mNotificationBuilder.build());
            }

            @Override
            protected Void doInBackground(String... strings) {
                HttpURLConnection conn = null;
                try {
                    mAppLink = strings[INDEX_APP_LINK];
                    URL url = new URL(mAppLink);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(TIMEOUT_CONNECTION);
                    conn.setReadTimeout(TIMEOUT_READ);

                    mApkLength = conn.getContentLength();
                    //noinspection StatementWithEmptyBody
                    if (isCancelled()) {

                    } else if (mApkLength <= 0) {
                        onDownloadError();
                    } else {
                        mApk = new File(Files.getAppExternalFilesDir(),
                                strings[INDEX_APP_NAME] + " "
                                        + strings[INDEX_VERSION_NAME].replace(".", "_")
                                        + ".apk");
                        if (mApk.exists()) {
                            final String sha1 = strings[INDEX_APP_SHA1];
                            // 如果应用已经下载过了，则直接弹出安装提示通知
                            if (mApk.length() == mApkLength
                                    && ObjectsCompat.equals(FileUtils.getFileSha1(mApk), sha1)) {
                                getHandler().post(() -> {
                                    if (!isCancelled()) {
                                        if (Configs.DEBUG_APP_UPDATE) {
                                            Log.d(TAG, "Stop service when request apk is already"
                                                    + " downloaded.");
                                        }
                                        stopServiceAndShowInstallAppPrompt();
                                    }
                                });
                                return null;
                                // 否则先删除旧的apk
                            } else {
                                //noinspection ResultOfMethodCallIgnored
                                mApk.delete();
                            }
                        }
                        if (FileUtils.hasEnoughStorageOnDisk(mApkLength)) {
                            getHandler().post(() -> {
                                if (!isCancelled()) {
                                    final int blockSize = mApkLength / COUNT_DOWNLOAD_APP_TASK;
                                    mDownloadAppTasks = new ArrayList<>(COUNT_DOWNLOAD_APP_TASK);
                                    for (int i = 0; i < COUNT_DOWNLOAD_APP_TASK; i++) {
                                        final int start = i * blockSize;
                                        final int end = i == COUNT_DOWNLOAD_APP_TASK - 1 ?
                                                mApkLength : (i + 1) * blockSize - 1;
                                        mDownloadAppTasks.add(new DownloadAppTask());
                                        mDownloadAppTasks.get(i).executeOnExecutor(
                                                Executors.THREAD_POOL_EXECUTOR, start, end);
                                    }
                                }
                            });
                        } else {
                            getHandler().post(() -> {
                                if (!isCancelled()) {
                                    cancels(false);
                                    Toast.makeText(mContext, R.string.notHaveEnoughStorage,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                        }
                    }
                } catch (ConnectTimeoutException e) {
                    onConnectionTimeout();
                } catch (SocketTimeoutException e) {
                    onReadTimeout();
                } catch (IOException e) {
                    e.printStackTrace();
                    onDownloadError();
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
                return null;
            }

            void cancels(boolean removeNotificationNow) {
                if (Configs.DEBUG_APP_UPDATE) {
                    Log.d(TAG, "Cancel tasks that are still active.");
                }
                cancel(false);
                if (mDownloadAppTasks != null) {
                    for (DownloadAppTask task : mDownloadAppTasks) {
                        task.cancel(false);
                    }
                }

                deleteApk();

                stopService(removeNotificationNow);
            }

            private void deleteApk() {
                if (mApk != null) {
                    //noinspection ResultOfMethodCallIgnored
                    Executors.THREAD_POOL_EXECUTOR.execute(mApk::delete);
                }
            }

            @Synthetic void stopService(boolean removeNotificationNow) {
                mService.mTask = null;
                if (Configs.DEBUG_APP_UPDATE) {
                    Log.d(TAG, "Waiting for any progress notification to complete to be sent. "
                            + "removeNotificationNow=" + removeNotificationNow);
                }
                // Sync always to wait for any progress notification to complete to be sent...
                synchronized (mNotificationManager) {
                    if (removeNotificationNow) {
                        mService.stopForeground(false);
                        mNotificationManager.cancel(ID_NOTIFICATION);
                    }
                }
                getHandler().sendEmptyMessage(H.MSG_STOP_UPDATE_APP_SERVICE);
            }

            @Synthetic void stopServiceAndShowInstallAppPrompt() {
                stopService(false);

                // Postpone showing a notification to remind the user to install the downloaded app,
                // because the Context's stopService method will asynchronously cancel
                // the notification with id ID_NOTIFICATION, resulting in the pending notification
                // to be canceled as the service dies.
                Handler handler = getHandler();
                handler.post(() -> {
                    Runnable action = () -> {
                        if (Configs.DEBUG_APP_UPDATE) {
                            Log.d(TAG, "Show app installation prompt notification now.");
                        }
                        showInstallAppPromptNotification(mApk);
                    };
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Configs.DEBUG_APP_UPDATE) {
                            Log.d(TAG, "Start checking if any notifications use id " + ID_NOTIFICATION);
                        }
                        postTillConditionMeets(handler, action,
                                () -> !hasNotification(mNotificationManager, ID_NOTIFICATION, null));
                    } else {
                        if (Configs.DEBUG_APP_UPDATE) {
                            Log.d(TAG, "Postpone showing app installation prompt notification for "
                                    + Configs.DELAY_SEND_NOTIFICATION_WITH_JUST_STOPPED_FOREGROUND_SERVICE_ID
                                    + " milliseconds.");
                        }
                        // FIXME: the upcoming prompt notification may still be canceled by
                        //        ActivityManagerService though this is a small probability
                        //        event, since it is running on the server process and we can't
                        //        ensure the action will only be performed after it cancels
                        //        the notification with the same id.
                        handler.postDelayed(action,
                                Configs.DELAY_SEND_NOTIFICATION_WITH_JUST_STOPPED_FOREGROUND_SERVICE_ID);
                    }
                });
            }

            void showInstallAppPromptNotification(File apk) {
                if (apk == null || !apk.exists() || apk.length() != mApkLength) {
                    Toast.makeText(mContext, R.string.theInstallationPackageHasBeenDamaged,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent it = Utils.createPackageInstaller(mContext, apk);

//                mContext.startActivity(it); // MIUI默认应用在后台时无法弹出界面

                String channelId = NotificationChannelManager.getMessageNotificationChannelId(mContext);
                String title = mContext.getString(R.string.newAppDownloaded);
                @SuppressLint("UnspecifiedImmutableFlag")
                PendingIntent pi = PendingIntent.getActivity(mContext, 0, it, 0);
                mNotificationManager.notify(
                        ID_NOTIFICATION,
                        mNotificationBuilder
                                .setChannelId(channelId)
                                .setTicker(title)
                                .setContentTitle(title)
                                .setContentText(mContext.getString(R.string.clickToInstallIt))
                                .setContentIntent(pi)
//                                .setFullScreenIntent(pi, true)
                                .setAutoCancel(true)
                                .setCustomContentView(null)
                                .setCustomBigContentView(null)
                                .setStyle(null)
                                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS)
                                .setOnlyAlertOnce(false)
                                .setPriority(NotificationCompat.PRIORITY_HIGH) // 高优先级以显示抬头式通知
                                .setCategory(NotificationCompat.CATEGORY_PROMO)
                                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                                .build());
            }

            Handler getHandler() {
                return AppUpdateChecker.getSingleton(mContext).mH;
            }

            @Synthetic void onConnectionTimeout() {
                if (!isCancelled()) {
                    getHandler().post(() -> {
                        cancels(false);
                        Toast.makeText(mContext,
                                R.string.connectionTimeout, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Synthetic void onReadTimeout() {
                if (!isCancelled()) {
                    getHandler().post(() -> {
                        cancels(false);
                        Toast.makeText(mContext,
                                R.string.readTimeout, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Synthetic void onDownloadError() {
                if (!isCancelled()) {
                    getHandler().post(() -> {
                        cancels(false);
                        Toast.makeText(mContext,
                                R.string.downloadError, Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @SuppressLint("StaticFieldLeak")
            final class DownloadAppTask extends AsyncTask<Integer, Integer, Void> {
                final UpdateAppTask mHost = UpdateAppTask.this;

                @Override
                protected Void doInBackground(Integer... indices) {
                    final int startIndex = indices[0];
                    final int endIndex = indices[1];

                    HttpURLConnection conn = null;
                    InputStream in = null;
                    RandomAccessFile out = null;
                    try {
                        URL url = new URL(mAppLink);
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(TIMEOUT_CONNECTION);
//                        conn.setReadTimeout(TIMEOUT_READ);
                        // 重要：请求服务器下载部分文件 指定文件的位置
                        conn.setRequestProperty("Range", "bytes=" + startIndex + "-" + endIndex);

                        // 从服务器请求全部资源返回 200 ok；从服务器请求部分资源返回 206 ok
                        // final int responseCode = conn.getResponseCode();

                        in = conn.getInputStream();

                        out = new RandomAccessFile(mApk, "rwd");
                        out.seek(startIndex);

                        int len;
                        final byte[] buffer = new byte[8 * 1024];
                        while (!mHost.isCancelled() && (len = in.read(buffer)) != -1) {
                            out.write(buffer, 0, len);

                            if (!mHost.isCancelled()) {
//                                publishProgress(len);
                                notifyProgressUpdated(mProgress.addAndGet(len));
                            }
                        }
                    } catch (ConnectTimeoutException e) {
                        onConnectionTimeout();
                    } catch (SocketTimeoutException e) {
                        onReadTimeout();
                    } catch (IOException e) {
                        e.printStackTrace();
                        onDownloadError();
                    } finally {
                        IOUtils.closeSilently(out);
                        IOUtils.closeSilently(in);
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }

                    return null;
                }

                @Override
                protected void onProgressUpdate(Integer... values) {
//                    notifyProgressUpdated(mProgress += values[0]);
                }

                private void notifyProgressUpdated(int progress) {
                    float progressPercent = (float) progress / (float) mApkLength * 100f;
                    RemoteViews nv = createNotificationView();
                    nv.setProgressBar(R.id.progress, mApkLength, progress, false);
                    nv.setTextViewText(R.id.text_percentProgress,
                            mContext.getString(R.string.percentProgress, progressPercent));
                    nv.setTextViewText(R.id.text_charsequenceProgress,
                            mContext.getString(R.string.charsequenceProgress,
                                    FileUtils.formatFileSize(progress),
                                    FileUtils.formatFileSize(mApkLength)));

                    Notification n;
                    synchronized (mNotificationBuilder) {
                        n = mNotificationBuilder
                                .setCustomContentView(nv)
                                .setCustomBigContentView(nv)
                                .build();
                    }
                    // Ensures no more progress notification to be sent when download is canceled...
                    synchronized (mNotificationManager) {
                        if (mService.mTask != null && !mHost.isCancelled()) {
                            if (Configs.DEBUG_APP_UPDATE) {
                                Log.d(TAG, "Post a new progress notification into the status bar. "
                                        + "progressPercent=" + progressPercent);
                            }
                            mNotificationManager.notify(ID_NOTIFICATION, n);
                        }
                    }
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mDownloadAppTasks.remove(this);
                    if (mDownloadAppTasks.isEmpty() && !mHost.isCancelled()) {
                        if (Configs.DEBUG_APP_UPDATE) {
                            Log.d(TAG, "All download tasks are completed. Stop service now...");
                        }
                        stopServiceAndShowInstallAppPrompt();
                    }
                }
            }
        }
    }
}
