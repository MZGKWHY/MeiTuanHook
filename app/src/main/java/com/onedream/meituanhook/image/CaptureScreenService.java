package com.onedream.meituanhook.image;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;

import com.onedream.meituanhook.system.ScreenHelper;
import com.onedream.meituanhook.shared_preferences.ScreenLocalStorage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CaptureScreenService extends Service {
    private static final String TAG = "ATU";
    private MediaProjection mMediaProjection = null;
    private VirtualDisplay mVirtualDisplay = null;
    private int windowWidth = 0;
    private int windowHeight = 0;
    private ImageReader mImageReader = null;
    private int mScreenDensity = 0;
    private static int intentResultCode;
    private static Intent intent;
    private static MediaProjectionManager mMediaProjectionManager;

    public static int getIntentResultCode() {
        return intentResultCode;
    }

    public static Intent getIntent() {
        return intent;
    }

    public static MediaProjectionManager getMediaProjectionManager() {
        return mMediaProjectionManager;
    }

    public static void setIntentResultCode(int result1) {
        intentResultCode = result1;
    }

    public static void setIntent(Intent intent1) {
        intent = intent1;
    }

    public static void setMediaProjectionManager(MediaProjectionManager mMediaProjectionManager1) {
        mMediaProjectionManager = mMediaProjectionManager1;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, CaptureScreenService.class);
        context.stopService(intent);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        requestCapture();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void requestCapture() {
        createVirtualEnvironment();
        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
            public void run() {
                //start virtual
                startVirtual();
            }
        }, 500);

        Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable() {
            public void run() {
                //capture the screen
                startCapture();
            }
        }, 1500);

        Handler handler3 = new Handler();
        handler3.postDelayed(new Runnable() {
            public void run() {
                stopVirtual();
            }
        }, 1000);
    }

    private void createVirtualEnvironment() {
        DisplayMetrics dm = ScreenHelper.INSTANCE.getDisplayMetrics(this);
        mScreenDensity = (int) dm.density;
        windowWidth = dm.widthPixels;
        windowHeight = ScreenLocalStorage.getScreenHeight(dm.heightPixels);//todo 这里的高度需要校正
        //
        printLog("校准后的高度为"+windowHeight);
        //
        mImageReader = ImageReader.newInstance(windowWidth, windowHeight, 0x1, 2); //ImageFormat.RGB_565

        printLog("prepared the virtual environment");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void startVirtual() {
        if (mMediaProjection != null) {
            printLog("want to display virtual");
            virtualDisplay();
        } else {
            printLog("start screen capture intent");
            printLog( "want to build mediaprojection and display virtual");
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void setUpMediaProjection() {
        mMediaProjection = mMediaProjectionManager.getMediaProjection(intentResultCode, getIntent());
        printLog( "mMediaProjection defined");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                windowWidth, windowHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
        printLog("virtual displayed");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startCapture() {
        Image image = mImageReader.acquireLatestImage();
        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();
        printLog("image data captured");

        if (bitmap != null) {
            try {
                String nameImage = ImageConfigureHelper.INSTANCE.getCurrentScreenPicturePath();
                File fileImage = new File(nameImage);
                if (!fileImage.exists()) {
                    fileImage.createNewFile();
                    printLog("image file created");
                }
                FileOutputStream out = new FileOutputStream(fileImage);
                if (out != null) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                    out.flush();
                    out.close();
                    Intent media = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    Uri contentUri = Uri.fromFile(fileImage);
                    media.setData(contentUri);
                    this.sendBroadcast(media);
                    printLog("screen image saved");
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                printLog("screen image FileNotFoundException=" +e.toString());
            } catch (IOException e) {
                e.printStackTrace();
                printLog("screen image IOException=" +e.toString());
            }
        }
    }

    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
        printLog( "virtual display stopped");
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
        printLog("mMediaProjection undefined");
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        tearDownMediaProjection();
    }


    private void printLog(String message){
        Log.e("ATU capture", message+"");
    }
}