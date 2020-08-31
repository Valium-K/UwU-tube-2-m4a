package com.sive.uwutubedownloader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.view.MotionEvent;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.github.kiulian.downloader.OnYoutubeDownloadListener;
import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.YoutubeException;
import com.github.kiulian.downloader.model.VideoDetails;
import com.github.kiulian.downloader.model.YoutubeVideo;
import com.github.kiulian.downloader.model.formats.Format;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    static boolean isDownloading = false;
    static int threadCounter = 0;

    static final int TARGET_TOUCH_COUNT = 15;
    static int easterEggCount = 0;

    static int notificationId = 1242;

    static final int PERMISSION_REQUEST_CODE = 1;
    static final String[] PERMISSIONS = {
            "android.permission.INTERNET",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.WAKE_LOCK",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.ACCESS_NETWORK_STATE"
    };

    PowerManager powerManager = null;
    PowerManager.WakeLock wakeLock = null;
    WifiManager.WifiLock wifiLock = null;
    Context context = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        easterEggCount = 0;
        final Intent intent = getIntent();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionCheck();


        if(intent.getAction() == Intent.ACTION_SEND) {
            if("text/plain".equals(intent.getType()) &&
                    intent.getStringExtra("android.intent.extra.TEXT").contains("youtu")) {

                permissionCheck();

                context = getApplicationContext();
                wifiManagerInit();
                powerManagerInit();

                acquireWifiNWakeLock();

                wifiNWakeLockTimer();

                new Thread(() -> {
                    try {
                        if(handleSentText(intent))
                            showToastMessage("Downloading m4a now.");
                        else {
                            // fail
                            threadCounter--;
                        }

                    } catch (YoutubeException e) {
                        showToastMessage("YoutubeException: " + e.getMessage());
                    } catch (IOException e) {
                        showToastMessage("IOException: " + e.getMessage());
                    }
                }).start();

            }
            else Toast.makeText(MainActivity.this, "Youtube link only", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                easterEggCount++;
                if(easterEggCount >= TARGET_TOUCH_COUNT) {
                    Toast.makeText(this, " *UwU* ", Toast.LENGTH_SHORT).show();
                }
        }
        return super.onTouchEvent(event);
    }
    private boolean hasPermissions(String[] permissions) {
        int res = 0;

        for (String perms : permissions){
            res = checkCallingOrSelfPermission(perms);
            if (!(res == PackageManager.PERMISSION_GRANTED)){
                //퍼미션 허가 안된 경우
                return false;
            }

        }

        return true;
    }
    private void requestNecessaryPermissions(String[] permissions) {
        //마시멜로( API 23 )이상에서 런타임 퍼미션(Runtime Permission) 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(permissions, PERMISSION_REQUEST_CODE);
        }
    }
    private void permissionCheck() {
        if (!hasPermissions(PERMISSIONS))
            requestNecessaryPermissions(PERMISSIONS);
        // else System.out.println("필요한 모든 권한을 가짐.");
    }
    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            String description = getString(R.string.app_name);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("CHANNEL", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
    private void wifiManagerInit() {
        if(wifiLock == null) {
            WifiManager wifiManager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
            wifiLock = wifiManager.createWifiLock("wifilock");
            wifiLock.setReferenceCounted(true);
        }
    }
    private void powerManagerInit() {
        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "MyApp::MyWakelockTag");
    }
    private boolean acquireWifiNWakeLock() {
        if ((wifiLock != null) &&
                (wifiLock.isHeld() == false)) {
            wifiLock.acquire();
            wakeLock.acquire();
            return true;
        }
        return false;
    }
    private boolean wifiNWakeLockTimer() {
        if(!isDownloading) {
            new Thread(() -> {
                isDownloading = true;
                while(isDownloading) {
                    try {
                        Thread.sleep(10000);

                        if(threadCounter <= 0) {
                            //System.out.println("wakeLock.release()");
                            isDownloading = false;
                            wakeLock.release();
                            wifiLock.release();
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            return true;
        }
        return false;
    }
    private void showToastMessage(String s) {
        MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this,
                s,
                Toast.LENGTH_SHORT).show());
    }

    private boolean handleSentText(Intent intent) throws YoutubeException, IOException {
        Format target = null;
        List<Format> targetAudios = new ArrayList<>();

        isDownloading = true;
        threadCounter++;

        // init
        YoutubeDownloader downloader = new YoutubeDownloader();
        downloader.setParserRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36");
        downloader.setParserRetryOnFailure(1);

        // get VideoDetails
        String uri = intent.getStringExtra("android.intent.extra.TEXT");
        String videoId = uri.substring(17);
        YoutubeVideo video = downloader.getVideo(videoId);
        VideoDetails details = video.details();
        String title = details.title();

        // 타이틀 50자 이내
        if(title.length() >= 50) {
            title = title.substring(0, 50);
        }

        // live videos and streams
        if (video.details().isLive()) {
            showToastMessage("Can't convert live-stream to m4a");

            return false;
        }

        // 비디오 코덱 mp4a add
        video.formats().forEach( a -> {
            if(a.mimeType().equals("audio/mp4; codecs=\"mp4a.40.2\"")) {
                targetAudios.add(a);
            }
        });

        // Start Download
        File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), "UwU tube");

        if(!targetAudios.isEmpty())
            target = targetAudios.get(0);
        else {
            showToastMessage("UwU doesn't know that kind of Video Type :(");
            return false;
        }

//        else {
//            showToastMessage("Force convert to mp3");
//
//            // get videos with audio
//            List<AudioVideoFormat> tinies = new ArrayList<>();
//            List<AudioVideoFormat> highs = new ArrayList<>();
//            List<AudioVideoFormat> mediums = new ArrayList<>();
//            List<AudioVideoFormat> lows = new ArrayList<>();
//            List<AudioVideoFormat> videoWithAudioFormats = video.videoWithAudioFormats();
//
//            videoWithAudioFormats.forEach(it -> {
//                if(it.videoQuality() == VideoQuality.tiny) {
//                    tinies.add(it);
//                }
//            });
//
//            tinies.forEach(v -> {
//                if(v.audioQuality() == AudioQuality.high) highs.add(v);
//                else if(v.audioQuality() == AudioQuality.medium) mediums.add(v);
//                else if(v.audioQuality() == AudioQuality.low) lows.add(v);
//            });
//
//            Map<String, Format> targetVideosAndAudio = new HashMap<>();
//
//            // Mapping Audio
//            if(!highs.isEmpty()) targetVideosAndAudio.put("Audio", highs.get(0));
//            else if(!mediums.isEmpty()) targetVideosAndAudio.put("Audio", mediums.get(0));
//            else if(!lows.isEmpty()) targetVideosAndAudio.put("Audio", lows.get(0));
//
//            target = targetVideosAndAudio.get("Audio");
//        }

        // noti bar settings
        int notificationIdForThread = notificationId;
        notificationId++;

        createNotificationChannel();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        NotificationCompat.Builder notification = new NotificationCompat.Builder(this, "CHANNEL")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title + ".m4a")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(100, 0, false);

        notificationManager.notify(notificationIdForThread, notification.build());

        // async downloading with callback
        // this.moveTaskToBack(true);
        video.downloadAsync(target, outputDir, title, false, new OnYoutubeDownloadListener() {
            @Override
            public void onDownloading(int progress, String fileName) {
                notification.setProgress(100, progress, false);
                notificationManager.notify(notificationIdForThread, notification.build());

                // System.out.printf("Downloaded %d%%\n", progress);
            }

            @Override
            public void onFinished(File file) {
                notificationManager.cancel(notificationIdForThread);
                notification.setContentText("Downloaded: /download/UwU tube").setProgress(0, 0, false).setOnlyAlertOnce(false).setOngoing(false);
                notificationManager.notify(notificationIdForThread, notification.build());

                System.out.println("Finished file: " + file);
                threadCounter--;
            }

            @Override
            public void onError(Throwable throwable) {
                MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Download Error: " + throwable.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        });

        return true;
    }
}
