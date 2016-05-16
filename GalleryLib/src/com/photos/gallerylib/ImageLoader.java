package com.photos.gallerylib;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import com.photos.nativeobject.TLObject;
import com.photos.nativeobject.TLRPC;
import com.photos.utils.AndroidUtilities;
import com.photos.utils.LogUtil;
import com.photos.utils.NotificationCenter;
import com.photos.utils.Utilities;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.media.ExifInterface;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

public class ImageLoader {
	private HashMap<String, Integer> bitmapUseCounts = new HashMap<>();
    private LruCache memCache;
    private HashMap<String, CacheImage> imageLoadingByUrl = new HashMap<>();
    private HashMap<String, CacheImage> imageLoadingByKeys = new HashMap<>();
    private HashMap<Integer, CacheImage> imageLoadingByTag = new HashMap<>();
    private HashMap<String, ThumbGenerateInfo> waitingForQualityThumb = new HashMap<>();
    private HashMap<Integer, String> waitingForQualityThumbByTag = new HashMap<>();
    private DispatchQueue cacheOutQueue = new DispatchQueue("cacheOutQueue");
    private DispatchQueue cacheThumbOutQueue = new DispatchQueue("cacheThumbOutQueue");
    private DispatchQueue thumbGeneratingQueue = new DispatchQueue("thumbGeneratingQueue");
    private DispatchQueue imageLoadQueue = new DispatchQueue("imageLoadQueue");
    private ConcurrentHashMap<String, Float> fileProgresses = new ConcurrentHashMap<>();
    private static byte[] bytes;
    private static byte[] bytesThumb;
    private static byte[] header = new byte[12];
    private static byte[] headerThumb = new byte[12];
    private int currentHttpTasksCount = 0;

    private HashMap<String, Runnable> retryHttpsTasks = new HashMap<>();
    private int currentHttpFileLoadTasksCount = 0;

    public VMRuntimeHack runtimeHack = null;
    private String ignoreRemoval = null;

    private volatile long lastCacheOutTime = 0;
    private int lastImageNum = 0;
    private long lastProgressUpdateTime = 0;

    private File telegramPath = null;

    private class ThumbGenerateInfo {
        private int count;
        private TLRPC.FileLocation fileLocation;
        private String filter;
    }


    private class CacheOutTask implements Runnable {
        private Thread runningThread;
        private final Object sync = new Object();

        private CacheImage cacheImage;
        private boolean isCancelled;

        public CacheOutTask(CacheImage image) {
            cacheImage = image;
        }

        @Override
        public void run() {
            synchronized (sync) {
                runningThread = Thread.currentThread();
                Thread.interrupted();
                if (isCancelled) {
                    return;
                }
            }

            if (cacheImage.animatedFile) {
                synchronized (sync) {
                    if (isCancelled) {
                        return;
                    }
                }
                AnimatedFileDrawable fileDrawable = new AnimatedFileDrawable(cacheImage.finalFilePath, cacheImage.filter != null && cacheImage.filter.equals("d"));
                Thread.interrupted();
                onPostExecute(fileDrawable);
            } else {
                Long mediaId = null;
                boolean mediaIsVideo = false;
                Bitmap image = null;
                File cacheFileFinal = cacheImage.finalFilePath;
                boolean canDeleteFile = true;
                boolean useNativeWebpLoaded = false;

                if (Build.VERSION.SDK_INT < 19) {
                    RandomAccessFile randomAccessFile = null;
                    try {
                        randomAccessFile = new RandomAccessFile(cacheFileFinal, "r");
                        byte[] bytes;
                        if (cacheImage.thumb) {
                            bytes = headerThumb;
                        } else {
                            bytes = header;
                        }
                        randomAccessFile.readFully(bytes, 0, bytes.length);
                        String str = new String(bytes).toLowerCase();
                        str = str.toLowerCase();
                        if (str.startsWith("riff") && str.endsWith("webp")) {
                            useNativeWebpLoaded = true;
                        }
                        randomAccessFile.close();
                    } catch (Exception e) {
                        LogUtil.e("tmessages", e);
                    } finally {
                        if (randomAccessFile != null) {
                            try {
                                randomAccessFile.close();
                            } catch (Exception e) {
                                LogUtil.e("tmessages", e);
                            }
                        }
                    }
                }

                if (cacheImage.thumb) {
                    int blurType = 0;
                    if (cacheImage.filter != null) {
                        if (cacheImage.filter.contains("b2")) {
                            blurType = 3;
                        } else if (cacheImage.filter.contains("b1")) {
                            blurType = 2;
                        } else if (cacheImage.filter.contains("b")) {
                            blurType = 1;
                        }
                    }

                    try {
                        lastCacheOutTime = System.currentTimeMillis();
                        synchronized (sync) {
                            if (isCancelled) {
                                return;
                            }
                        }

                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 1;

                        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 21) {
                            opts.inPurgeable = true;
                        }

                        if (useNativeWebpLoaded) {
                            RandomAccessFile file = new RandomAccessFile(cacheFileFinal, "r");
                            ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal.length());

                            BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                            bmOptions.inJustDecodeBounds = true;
                            Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                            image = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);

                            Utilities.loadWebpImage(image, buffer, buffer.limit(), null, !opts.inPurgeable);
                            file.close();
                        } else {
                            if (opts.inPurgeable) {
                                RandomAccessFile f = new RandomAccessFile(cacheFileFinal, "r");
                                int len = (int) f.length();
                                byte[] data = bytesThumb != null && bytesThumb.length >= len ? bytesThumb : null;
                                if (data == null) {
                                    bytesThumb = data = new byte[len];
                                }
                                f.readFully(data, 0, len);
                                image = BitmapFactory.decodeByteArray(data, 0, len, opts);
                            } else {
                                FileInputStream is = new FileInputStream(cacheFileFinal);
                                image = BitmapFactory.decodeStream(is, null, opts);
                                is.close();
                            }
                        }

                        if (image == null) {
                            if (cacheFileFinal.length() == 0 || cacheImage.filter == null) {
                                cacheFileFinal.delete();
                            }
                        } else {
                            if (blurType == 1) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 3, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 2) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 1, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 3) {
                                if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                    Utilities.blurBitmap(image, 7, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                }
                            } else if (blurType == 0 && opts.inPurgeable) {
                                Utilities.pinBitmap(image);
                            }
                            if (runtimeHack != null) {
                                runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                            }
                        }
                    } catch (Throwable e) {
                        LogUtil.e("tmessages", e);
                    }
                } else {
                    try {
                        if (cacheImage.httpUrl != null) {
                            if (cacheImage.httpUrl.startsWith("thumb://")) {
                                int idx = cacheImage.httpUrl.indexOf(":", 8);
                                if (idx >= 0) {
                                    mediaId = Long.parseLong(cacheImage.httpUrl.substring(8, idx));
                                    mediaIsVideo = false;
                                }
                                canDeleteFile = false;
                            } else if (cacheImage.httpUrl.startsWith("vthumb://")) {
                                int idx = cacheImage.httpUrl.indexOf(":", 9);
                                if (idx >= 0) {
                                    mediaId = Long.parseLong(cacheImage.httpUrl.substring(9, idx));
                                    mediaIsVideo = true;
                                }
                                canDeleteFile = false;
                            } else if (!cacheImage.httpUrl.startsWith("http")) {
                                canDeleteFile = false;
                            }
                        }

                        int delay = 20;
                        if (runtimeHack != null) {
                            delay = 60;
                        }
                        if (mediaId != null) {
                            delay = 0;
                        }
                        if (delay != 0 && lastCacheOutTime != 0 && lastCacheOutTime > System.currentTimeMillis() - delay && Build.VERSION.SDK_INT < 21) {
                            Thread.sleep(delay);
                        }
                        lastCacheOutTime = System.currentTimeMillis();
                        synchronized (sync) {
                            if (isCancelled) {
                                return;
                            }
                        }

                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 1;

                        float w_filter = 0;
                        float h_filter = 0;
                        boolean blur = false;
                        if (cacheImage.filter != null) {
                            String args[] = cacheImage.filter.split("_");
                            if (args.length >= 2) {
                                w_filter = Float.parseFloat(args[0]) * AndroidUtilities.density;
                                h_filter = Float.parseFloat(args[1]) * AndroidUtilities.density;
                            }
                            if (cacheImage.filter.contains("b")) {
                                blur = true;
                            }
                            if (w_filter != 0 && h_filter != 0) {
                                opts.inJustDecodeBounds = true;

                                if (mediaId != null) {
                                    if (mediaIsVideo) {
                                        MediaStore.Video.Thumbnails.getThumbnail(AndroidUtilities.mainContext.getContentResolver(), mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                                    } else {
                                        MediaStore.Images.Thumbnails.getThumbnail(AndroidUtilities.mainContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                                    }
                                } else {
                                    FileInputStream is = new FileInputStream(cacheFileFinal);
                                    image = BitmapFactory.decodeStream(is, null, opts);
                                    is.close();
                                }

                                float photoW = opts.outWidth;
                                float photoH = opts.outHeight;
                                float scaleFactor = Math.max(photoW / w_filter, photoH / h_filter);
                                if (scaleFactor < 1) {
                                    scaleFactor = 1;
                                }
                                opts.inJustDecodeBounds = false;
                                opts.inSampleSize = (int) scaleFactor;
                            }
                        }
                        synchronized (sync) {
                            if (isCancelled) {
                                return;
                            }
                        }

                        if (cacheImage.filter == null || blur || cacheImage.httpUrl != null) {
                            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                        } else {
                            opts.inPreferredConfig = Bitmap.Config.RGB_565;
                        }
                        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 21) {
                            opts.inPurgeable = true;
                        }

                        opts.inDither = false;
                        if (mediaId != null) {
                            if (mediaIsVideo) {
                                image = MediaStore.Video.Thumbnails.getThumbnail(AndroidUtilities.mainContext.getContentResolver(), mediaId, MediaStore.Video.Thumbnails.MINI_KIND, opts);
                            } else {
                                image = MediaStore.Images.Thumbnails.getThumbnail(AndroidUtilities.mainContext.getContentResolver(), mediaId, MediaStore.Images.Thumbnails.MINI_KIND, opts);
                            }
                        }
                        if (image == null) {
                            if (useNativeWebpLoaded) {
                                RandomAccessFile file = new RandomAccessFile(cacheFileFinal, "r");
                                ByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFileFinal.length());

                                BitmapFactory.Options bmOptions = new BitmapFactory.Options();
                                bmOptions.inJustDecodeBounds = true;
                                Utilities.loadWebpImage(null, buffer, buffer.limit(), bmOptions, true);
                                image = Bitmaps.createBitmap(bmOptions.outWidth, bmOptions.outHeight, Bitmap.Config.ARGB_8888);

                                Utilities.loadWebpImage(image, buffer, buffer.limit(), null, !opts.inPurgeable);
                                file.close();
                            } else {
                                if (opts.inPurgeable) {
                                    RandomAccessFile f = new RandomAccessFile(cacheFileFinal, "r");
                                    int len = (int) f.length();
                                    byte[] data = bytes != null && bytes.length >= len ? bytes : null;
                                    if (data == null) {
                                        bytes = data = new byte[len];
                                    }
                                    f.readFully(data, 0, len);
                                    image = BitmapFactory.decodeByteArray(data, 0, len, opts);
                                } else {
                                    FileInputStream is = new FileInputStream(cacheFileFinal);
                                    image = BitmapFactory.decodeStream(is, null, opts);
                                    is.close();
                                }
                            }
                        }
                        if (image == null) {
                            if (canDeleteFile && (cacheFileFinal.length() == 0 || cacheImage.filter == null)) {
                                cacheFileFinal.delete();
                            }
                        } else {
                            boolean blured = false;
                            if (cacheImage.filter != null) {
                                float bitmapW = image.getWidth();
                                float bitmapH = image.getHeight();
                                if (!opts.inPurgeable && w_filter != 0 && bitmapW != w_filter && bitmapW > w_filter + 20) {
                                    float scaleFactor = bitmapW / w_filter;
                                    Bitmap scaledBitmap = Bitmaps.createScaledBitmap(image, (int) w_filter, (int) (bitmapH / scaleFactor), true);
                                    if (image != scaledBitmap) {
                                        image.recycle();
                                        image = scaledBitmap;
                                    }
                                }
                                if (image != null && blur && bitmapH < 100 && bitmapW < 100) {
                                    if (image.getConfig() == Bitmap.Config.ARGB_8888) {
                                        Utilities.blurBitmap(image, 3, opts.inPurgeable ? 0 : 1, image.getWidth(), image.getHeight(), image.getRowBytes());
                                    }
                                    blured = true;
                                }
                            }
                            if (!blured && opts.inPurgeable) {
                                Utilities.pinBitmap(image);
                            }
                            if (runtimeHack != null && image != null) {
                                runtimeHack.trackFree(image.getRowBytes() * image.getHeight());
                            }
                        }
                    } catch (Throwable e) {
                        //don't promt
                    }
                }
                Thread.interrupted();
                onPostExecute(image != null ? new BitmapDrawable(image) : null);
            }
        }

        private void onPostExecute(final BitmapDrawable bitmapDrawable) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    BitmapDrawable toSet = null;
                    if (bitmapDrawable instanceof AnimatedFileDrawable) {
                        toSet = bitmapDrawable;
                    } else if (bitmapDrawable != null) {
                        toSet = memCache.get(cacheImage.key);
                        if (toSet == null) {
                            memCache.put(cacheImage.key, bitmapDrawable);
                            toSet = bitmapDrawable;
                        } else {
                            Bitmap image = bitmapDrawable.getBitmap();
                            if (runtimeHack != null) {
                                runtimeHack.trackAlloc(image.getRowBytes() * image.getHeight());
                            }
                            image.recycle();
                        }
                    }
                    final BitmapDrawable toSetFinal = toSet;
                    imageLoadQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            cacheImage.setImageAndClear(toSetFinal);
                        }
                    });
                }
            });
        }

        public void cancel() {
            synchronized (sync) {
                try {
                    isCancelled = true;
                    if (runningThread != null) {
                        runningThread.interrupt();
                    }
                } catch (Exception e) {
                    //don't promt
                }
            }
        }
    }

    public class VMRuntimeHack {
        private Object runtime = null;
        private Method trackAllocation = null;
        private Method trackFree = null;

        public boolean trackAlloc(long size) {
            if (runtime == null) {
                return false;
            }
            try {
                Object res = trackAllocation.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean) res : true;
            } catch (Exception e) {
                return false;
            }
        }

        public boolean trackFree(long size) {
            if (runtime == null) {
                return false;
            }
            try {
                Object res = trackFree.invoke(runtime, size);
                return (res instanceof Boolean) ? (Boolean) res : true;
            } catch (Exception e) {
                return false;
            }
        }

        @SuppressWarnings("unchecked")
        public VMRuntimeHack() {
            try {
                Class cl = Class.forName("dalvik.system.VMRuntime");
                Method getRt = cl.getMethod("getRuntime", new Class[0]);
                Object[] objects = new Object[0];
                runtime = getRt.invoke(null, objects);
                trackAllocation = cl.getMethod("trackExternalAllocation", new Class[]{long.class});
                trackFree = cl.getMethod("trackExternalFree", new Class[]{long.class});
            } catch (Exception e) {
                LogUtil.e("tmessages", e);
                runtime = null;
                trackAllocation = null;
                trackFree = null;
            }
        }
    }

    private class CacheImage {
        protected String key;
        protected String url;
        protected String filter;
        protected String ext;
        protected TLObject location;
        protected boolean animatedFile;

        protected File finalFilePath;
        protected File tempFilePath;
        protected boolean thumb;

        protected String httpUrl;
        protected CacheOutTask cacheTask;

        protected ArrayList<ImageReceiver> imageReceiverArray = new ArrayList<>();

        public void addImageReceiver(ImageReceiver imageReceiver) {
            boolean exist = false;
            for (ImageReceiver v : imageReceiverArray) {
                if (v == imageReceiver) {
                    exist = true;
                    break;
                }
            }
            if (!exist) {
                imageReceiverArray.add(imageReceiver);
                imageLoadingByTag.put(imageReceiver.getTag(thumb), this);
            }
        }

        public void removeImageReceiver(ImageReceiver imageReceiver) {
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver obj = imageReceiverArray.get(a);
                if (obj == null || obj == imageReceiver) {
                    imageReceiverArray.remove(a);
                    if (obj != null) {
                        imageLoadingByTag.remove(obj.getTag(thumb));
                    }
                    a--;
                }
            }
            if (imageReceiverArray.size() == 0) {
                for (int a = 0; a < imageReceiverArray.size(); a++) {
                    imageLoadingByTag.remove(imageReceiverArray.get(a).getTag(thumb));
                }
                imageReceiverArray.clear();
                if (location != null) {
                    if (location instanceof TLRPC.FileLocation) {
                        FileLoader.getInstance().cancelLoadFile((TLRPC.FileLocation) location, ext);
                    } else if (location instanceof TLRPC.Document) {
                        FileLoader.getInstance().cancelLoadFile((TLRPC.Document) location);
                    }
                }
                if (cacheTask != null) {
                    if (thumb) {
                        cacheThumbOutQueue.cancelRunnable(cacheTask);
                    } else {
                        cacheOutQueue.cancelRunnable(cacheTask);
                    }
                    cacheTask.cancel();
                    cacheTask = null;
                }
                if (url != null) {
                    imageLoadingByUrl.remove(url);
                }
                if (key != null) {
                    imageLoadingByKeys.remove(key);
                }
            }
        }

        public void setImageAndClear(final BitmapDrawable image) {
            if (image != null) {
                final ArrayList<ImageReceiver> finalImageReceiverArray = new ArrayList<>(imageReceiverArray);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (image instanceof AnimatedFileDrawable) {
                            boolean imageSet = false;
                            AnimatedFileDrawable fileDrawable = (AnimatedFileDrawable) image;
                            for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                                ImageReceiver imgView = finalImageReceiverArray.get(a);
                                if (imgView.setImageBitmapByKey(a == 0 ? fileDrawable : fileDrawable.makeCopy(), key, thumb, false)) {
                                    imageSet = true;
                                }
                            }
                            if (!imageSet) {
                                ((AnimatedFileDrawable) image).recycle();
                            }
                        } else {
                            for (int a = 0; a < finalImageReceiverArray.size(); a++) {
                                ImageReceiver imgView = finalImageReceiverArray.get(a);
                                imgView.setImageBitmapByKey(image, key, thumb, false);
                            }
                        }
                    }
                });
            }
            for (int a = 0; a < imageReceiverArray.size(); a++) {
                ImageReceiver imageReceiver = imageReceiverArray.get(a);
                imageLoadingByTag.remove(imageReceiver.getTag(thumb));
            }
            imageReceiverArray.clear();
            if (url != null) {
                imageLoadingByUrl.remove(url);
            }
            if (key != null) {
                imageLoadingByKeys.remove(key);
            }
        }
    }

    private static volatile ImageLoader Instance = null;

    public static ImageLoader getInstance() {
        ImageLoader localInstance = Instance;
        if (localInstance == null) {
            synchronized (ImageLoader.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new ImageLoader();
                }
            }
        }
        return localInstance;
    }

    public ImageLoader() {

        cacheOutQueue.setPriority(Thread.MIN_PRIORITY);
        cacheThumbOutQueue.setPriority(Thread.MIN_PRIORITY);
        thumbGeneratingQueue.setPriority(Thread.MIN_PRIORITY);
        imageLoadQueue.setPriority(Thread.MIN_PRIORITY);

        int cacheSize = Math.min(15, ((ActivityManager) AndroidUtilities.mainContext.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryClass() / 7) * 1024 * 1024;

        if (Build.VERSION.SDK_INT < 11) {
            runtimeHack = new VMRuntimeHack();
            cacheSize = 1024 * 1024 * 3;
        }
        memCache = new LruCache(cacheSize) {
            @Override
            protected int sizeOf(String key, BitmapDrawable value) {
                Bitmap b = value.getBitmap();
                if (Build.VERSION.SDK_INT < 12) {
                    return b.getRowBytes() * b.getHeight();
                } else {
                    return b.getByteCount();
                }
            }

            @Override
            protected void entryRemoved(boolean evicted, String key, final BitmapDrawable oldValue, BitmapDrawable newValue) {
                if (ignoreRemoval != null && key != null && ignoreRemoval.equals(key)) {
                    return;
                }
                final Integer count = bitmapUseCounts.get(key);
                if (count == null || count == 0) {
                    Bitmap b = oldValue.getBitmap();
                    if (runtimeHack != null) {
                        runtimeHack.trackAlloc(b.getRowBytes() * b.getHeight());
                    }
                    if (!b.isRecycled()) {
                        b.recycle();
                    }
                }
            }
        };


        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent intent) {
                LogUtil.e("tmessages", "file system changed");
                Runnable r = new Runnable() {
                    public void run() {
                        checkMediaPaths();
                    }
                };
                if (Intent.ACTION_MEDIA_UNMOUNTED.equals(intent.getAction())) {
                    AndroidUtilities.runOnUIThread(r, 1000);
                } else {
                    r.run();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        filter.addAction(Intent.ACTION_MEDIA_CHECKING);
        filter.addAction(Intent.ACTION_MEDIA_EJECT);
        filter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        filter.addAction(Intent.ACTION_MEDIA_NOFS);
        filter.addAction(Intent.ACTION_MEDIA_REMOVED);
        filter.addAction(Intent.ACTION_MEDIA_SHARED);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTABLE);
        filter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        filter.addDataScheme("file");
        AndroidUtilities.mainContext.registerReceiver(receiver, filter);

        HashMap<Integer, File> mediaDirs = new HashMap<>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                LogUtil.e("tmessages", e);
            }
        }
        try {
            new File(cachePath, ".nomedia").createNewFile();
        } catch (Exception e) {
            LogUtil.e("tmessages", e);
        }
        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        FileLoader.getInstance().setMediaDirs(mediaDirs);

        checkMediaPaths();
    }

    public void checkMediaPaths() {
        cacheOutQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Integer, File> paths = createMediaPaths();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        FileLoader.getInstance().setMediaDirs(paths);
                    }
                });
            }
        });
    }

    public HashMap<Integer, File> createMediaPaths() {
        HashMap<Integer, File> mediaDirs = new HashMap<>();
        File cachePath = AndroidUtilities.getCacheDir();
        if (!cachePath.isDirectory()) {
            try {
                cachePath.mkdirs();
            } catch (Exception e) {
                LogUtil.e("tmessages", e);
            }
        }
        try {
            new File(cachePath, ".nomedia").createNewFile();
        } catch (Exception e) {
            LogUtil.e("tmessages", e);
        }

        mediaDirs.put(FileLoader.MEDIA_DIR_CACHE, cachePath);
        LogUtil.e("tmessages", "cache path = " + cachePath);

        try {
            if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())) {
                telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
                telegramPath.mkdirs();

                if (telegramPath.isDirectory()) {
                    try {
                        File imagePath = new File(telegramPath, "Telegram Images");
                        imagePath.mkdir();
                        if (imagePath.isDirectory() && canMoveFiles(cachePath, imagePath, FileLoader.MEDIA_DIR_IMAGE)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_IMAGE, imagePath);
                            LogUtil.e("tmessages", "image path = " + imagePath);
                        }
                    } catch (Exception e) {
                        LogUtil.e("tmessages", e);
                    }

                    try {
                        File videoPath = new File(telegramPath, "Telegram Video");
                        videoPath.mkdir();
                        if (videoPath.isDirectory() && canMoveFiles(cachePath, videoPath, FileLoader.MEDIA_DIR_VIDEO)) {
                            mediaDirs.put(FileLoader.MEDIA_DIR_VIDEO, videoPath);
                            LogUtil.e("tmessages", "video path = " + videoPath);
                        }
                    } catch (Exception e) {
                        LogUtil.e("tmessages", e);
                    }

                    try {
                        File audioPath = new File(telegramPath, "Telegram Audio");
                        audioPath.mkdir();
                        if (audioPath.isDirectory() && canMoveFiles(cachePath, audioPath, FileLoader.MEDIA_DIR_AUDIO)) {
                            new File(audioPath, ".nomedia").createNewFile();
                            mediaDirs.put(FileLoader.MEDIA_DIR_AUDIO, audioPath);
                            LogUtil.e("tmessages", "audio path = " + audioPath);
                        }
                    } catch (Exception e) {
                        LogUtil.e("tmessages", e);
                    }

                    try {
                        File documentPath = new File(telegramPath, "Telegram Documents");
                        documentPath.mkdir();
                        if (documentPath.isDirectory() && canMoveFiles(cachePath, documentPath, FileLoader.MEDIA_DIR_DOCUMENT)) {
                            new File(documentPath, ".nomedia").createNewFile();
                            mediaDirs.put(FileLoader.MEDIA_DIR_DOCUMENT, documentPath);
                            LogUtil.e("tmessages", "documents path = " + documentPath);
                        }
                    } catch (Exception e) {
                        LogUtil.e("tmessages", e);
                    }
                }
            } else {
                LogUtil.e("tmessages", "this Android can't rename files");
            }
            MediaController.getInstance().checkSaveToGalleryFiles();
        } catch (Exception e) {
            LogUtil.e("tmessages", e);
        }

        return mediaDirs;
    }

    private boolean canMoveFiles(File from, File to, int type) {
        RandomAccessFile file = null;
        try {
            File srcFile = null;
            File dstFile = null;
            if (type == FileLoader.MEDIA_DIR_IMAGE) {
                srcFile = new File(from, "000000000_999999_temp.jpg");
                dstFile = new File(to, "000000000_999999.jpg");
            } else if (type == FileLoader.MEDIA_DIR_DOCUMENT) {
                srcFile = new File(from, "000000000_999999_temp.doc");
                dstFile = new File(to, "000000000_999999.doc");
            } else if (type == FileLoader.MEDIA_DIR_AUDIO) {
                srcFile = new File(from, "000000000_999999_temp.ogg");
                dstFile = new File(to, "000000000_999999.ogg");
            } else if (type == FileLoader.MEDIA_DIR_VIDEO) {
                srcFile = new File(from, "000000000_999999_temp.mp4");
                dstFile = new File(to, "000000000_999999.mp4");
            }
            byte[] buffer = new byte[1024];
            srcFile.createNewFile();
            file = new RandomAccessFile(srcFile, "rws");
            file.write(buffer);
            file.close();
            file = null;
            boolean canRename = srcFile.renameTo(dstFile);
            srcFile.delete();
            dstFile.delete();
            if (canRename) {
                return true;
            }
        } catch (Exception e) {
            LogUtil.e("tmessages", e);
        } finally {
            try {
                if (file != null) {
                    file.close();
                }
            } catch (Exception e) {
                LogUtil.e("tmessages", e);
            }
        }
        return false;
    }

    public Float getFileProgress(String location) {
        if (location == null) {
            return null;
        }
        return fileProgresses.get(location);
    }

    private void performReplace(String oldKey, String newKey) {
        BitmapDrawable b = memCache.get(oldKey);
        if (b != null) {
            ignoreRemoval = oldKey;
            memCache.remove(oldKey);
            memCache.put(newKey, b);
            ignoreRemoval = null;
        }
        Integer val = bitmapUseCounts.get(oldKey);
        if (val != null) {
            bitmapUseCounts.put(newKey, val);
            bitmapUseCounts.remove(oldKey);
        }
    }

    public void incrementUseCount(String key) {
        Integer count = bitmapUseCounts.get(key);
        if (count == null) {
            bitmapUseCounts.put(key, 1);
        } else {
            bitmapUseCounts.put(key, count + 1);
        }
    }

    public boolean decrementUseCount(String key) {
        Integer count = bitmapUseCounts.get(key);
        if (count == null) {
            return true;
        }
        if (count == 1) {
            bitmapUseCounts.remove(key);
            return true;
        } else {
            bitmapUseCounts.put(key, count - 1);
        }
        return false;
    }

    public void removeImage(String key) {
        bitmapUseCounts.remove(key);
        memCache.remove(key);
    }

    public boolean isInCache(String key) {
        return memCache.get(key) != null;
    }

    public void clearMemory() {
        memCache.evictAll();
    }

    private void removeFromWaitingForThumb(Integer TAG) {
        String location = waitingForQualityThumbByTag.get(TAG);
        if (location != null) {
            ThumbGenerateInfo info = waitingForQualityThumb.get(location);
            if (info != null) {
                info.count--;
                if (info.count == 0) {
                    waitingForQualityThumb.remove(location);
                }
            }
            waitingForQualityThumbByTag.remove(TAG);
        }
    }

    public void cancelLoadingForImageReceiver(final ImageReceiver imageReceiver, final int type) {
        if (imageReceiver == null) {
            return;
        }
        imageLoadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                int start = 0;
                int count = 2;
                if (type == 1) {
                    count = 1;
                } else if (type == 2) {
                    start = 1;
                }
                for (int a = start; a < count; a++) {
                    Integer TAG = imageReceiver.getTag(a == 0);
                    if (a == 0) {
                        removeFromWaitingForThumb(TAG);
                    }
                    if (TAG != null) {
                        CacheImage ei = imageLoadingByTag.get(TAG);
                        if (ei != null) {
                            ei.removeImageReceiver(imageReceiver);
                        }
                    }
                }
            }
        });
    }

    public BitmapDrawable getImageFromMemory(String key) {
        return memCache.get(key);
    }

    public BitmapDrawable getImageFromMemory(TLObject fileLocation, String httpUrl, String filter) {
        if (fileLocation == null && httpUrl == null) {
            return null;
        }
        String key = null;
        if (httpUrl != null) {
            key = Utilities.MD5(httpUrl);
        } else {
            if (fileLocation instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation location = (TLRPC.FileLocation) fileLocation;
                key = location.volume_id + "_" + location.local_id;
            } else if (fileLocation instanceof TLRPC.Document) {
                TLRPC.Document location = (TLRPC.Document) fileLocation;
                key = location.dc_id + "_" + location.id;
            }
        }
        if (filter != null) {
            key += "@" + filter;
        }
        return memCache.get(key);
    }

    private void replaceImageInCacheInternal(final String oldKey, final String newKey, final TLRPC.FileLocation newLocation) {
        ArrayList<String> arr = memCache.getFilterKeys(oldKey);
        if (arr != null) {
            for (int a = 0; a < arr.size(); a++) {
                String filter = arr.get(a);
                String oldK = oldKey + "@" + filter;
                String newK = newKey + "@" + filter;
                performReplace(oldK, newK);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldK, newK, newLocation);
            }
        } else {
            performReplace(oldKey, newKey);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReplacedPhotoInMemCache, oldKey, newKey, newLocation);
        }
    }

    public void replaceImageInCache(final String oldKey, final String newKey, final TLRPC.FileLocation newLocation, boolean post) {
        if (post) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    replaceImageInCacheInternal(oldKey, newKey, newLocation);
                }
            });
        } else {
            replaceImageInCacheInternal(oldKey, newKey, newLocation);
        }
    }

    public void putImageToCache(BitmapDrawable bitmap, String key) {
        memCache.put(key, bitmap);
    }


    private void createLoadOperationForImageReceiver(final ImageReceiver imageReceiver, final String key, final String url, final String ext, final TLObject imageLocation, final String httpLocation, final String filter, final int size, final boolean cacheOnly, final int thumb) {
        if (imageReceiver == null || url == null || key == null) {
            return;
        }
        Integer TAG = imageReceiver.getTag(thumb != 0);
        if (TAG == null) {
            imageReceiver.setTag(TAG = lastImageNum, thumb != 0);
            lastImageNum++;
            if (lastImageNum == Integer.MAX_VALUE) {
                lastImageNum = 0;
            }
        }

        final Integer finalTag = TAG;
        final boolean finalIsNeedsQualityThumb = imageReceiver.isNeedsQualityThumb();
        final boolean shouldGenerateQualityThumb = imageReceiver.isShouldGenerateQualityThumb();
        imageLoadQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                boolean added = false;
                if (thumb != 2) {
                    CacheImage alreadyLoadingUrl = imageLoadingByUrl.get(url);
                    CacheImage alreadyLoadingCache = imageLoadingByKeys.get(key);
                    CacheImage alreadyLoadingImage = imageLoadingByTag.get(finalTag);
                    if (alreadyLoadingImage != null) {
                        if (alreadyLoadingImage == alreadyLoadingUrl || alreadyLoadingImage == alreadyLoadingCache) {
                            added = true;
                        } else {
                            alreadyLoadingImage.removeImageReceiver(imageReceiver);
                        }
                    }

                    if (!added && alreadyLoadingCache != null) {
                        alreadyLoadingCache.addImageReceiver(imageReceiver);
                        added = true;
                    }
                    if (!added && alreadyLoadingUrl != null) {
                        alreadyLoadingUrl.addImageReceiver(imageReceiver);
                        added = true;
                    }
                }

                if (!added) {
                    boolean onlyCache = false;
                    boolean isQuality = false;
                    File cacheFile = null;

                    if (httpLocation != null) {
                        if (!httpLocation.startsWith("http")) {
                            onlyCache = true;
                            if (httpLocation.startsWith("thumb://")) {
                                int idx = httpLocation.indexOf(":", 8);
                                if (idx >= 0) {
                                    cacheFile = new File(httpLocation.substring(idx + 1));
                                }
                            } else if (httpLocation.startsWith("vthumb://")) {
                                int idx = httpLocation.indexOf(":", 9);
                                if (idx >= 0) {
                                    cacheFile = new File(httpLocation.substring(idx + 1));
                                }
                            } else {
                                cacheFile = new File(httpLocation);
                            }
                        }
                    } else if (thumb != 0) {
                        if (finalIsNeedsQualityThumb) {
                            cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), "q_" + url);
                            if (!cacheFile.exists()) {
                                cacheFile = null;
                            }
                        }

                    }

                    if (thumb != 2) {
                        CacheImage img = new CacheImage();

                        if (cacheFile == null) {
                            if (cacheOnly || size == 0 || httpLocation != null) {
                                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE), url);
                            } else if (imageLocation instanceof TLRPC.Document) {
                                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_DOCUMENT), url);
                            } else {
                                cacheFile = new File(FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_IMAGE), url);
                            }
                        }

                        img.thumb = thumb != 0;
                        img.key = key;
                        img.filter = filter;
                        img.httpUrl = httpLocation;
                        img.ext = ext;
                        img.addImageReceiver(imageReceiver);
                        if (onlyCache || cacheFile.exists()) {
                            img.finalFilePath = cacheFile;
                            img.cacheTask = new CacheOutTask(img);
                            imageLoadingByKeys.put(key, img);
                            if (thumb != 0) {
                                cacheThumbOutQueue.postRunnable(img.cacheTask);
                            } else {
                                cacheOutQueue.postRunnable(img.cacheTask);
                            }
                        } else {
                            img.url = url;
                            img.location = imageLocation;
                            imageLoadingByUrl.put(url, img);
                            if (httpLocation == null) {
                                if (imageLocation instanceof TLRPC.FileLocation) {
                                    TLRPC.FileLocation location = (TLRPC.FileLocation) imageLocation;
                                    FileLoader.getInstance().loadFile(location, ext, size, size == 0 || location.key != null || cacheOnly);
                                } else if (imageLocation instanceof TLRPC.Document) {
                                    FileLoader.getInstance().loadFile((TLRPC.Document) imageLocation, true, cacheOnly);
                                }
                            } else {
                            }
                        }
                    }
                }
            }
        });
    }

    public void loadImageForImageReceiver(ImageReceiver imageReceiver) {
        if (imageReceiver == null) {
            return;
        }

        String key = imageReceiver.getKey();
        if (key != null) {
            BitmapDrawable bitmapDrawable = memCache.get(key);
            if (bitmapDrawable != null) {
                cancelLoadingForImageReceiver(imageReceiver, 0);
                if (!imageReceiver.isForcePreview()) {
                    imageReceiver.setImageBitmapByKey(bitmapDrawable, key, false, true);
                    return;
                }
            }
        }
        boolean thumbSet = false;
        String thumbKey = imageReceiver.getThumbKey();
        if (thumbKey != null) {
            BitmapDrawable bitmapDrawable = memCache.get(thumbKey);
            if (bitmapDrawable != null) {
                imageReceiver.setImageBitmapByKey(bitmapDrawable, thumbKey, true, true);
                cancelLoadingForImageReceiver(imageReceiver, 1);
                thumbSet = true;
            }
        }

        TLRPC.FileLocation thumbLocation = imageReceiver.getThumbLocation();
        TLObject imageLocation = imageReceiver.getImageLocation();
        String httpLocation = imageReceiver.getHttpImageLocation();

        boolean saveImageToCache = false;

        String url = null;
        String thumbUrl = null;
        key = null;
        thumbKey = null;
        String ext = imageReceiver.getExt();
        if (ext == null) {
            ext = "jpg";
        }
        if (httpLocation != null) {
            key = Utilities.MD5(httpLocation);
            url = key + "." + getHttpUrlExtension(httpLocation, "jpg");
        } else if (imageLocation != null) {
            if (imageLocation instanceof TLRPC.FileLocation) {
                TLRPC.FileLocation location = (TLRPC.FileLocation) imageLocation;
                key = location.volume_id + "_" + location.local_id;
                url = key + "." + ext;
                if (imageReceiver.getExt() != null || location.key != null || location.volume_id == Integer.MIN_VALUE && location.local_id < 0) {
                    saveImageToCache = true;
                }
            } else if (imageLocation instanceof TLRPC.Document) {
                TLRPC.Document document = (TLRPC.Document) imageLocation;
                if (document.id == 0 || document.dc_id == 0) {
                    return;
                }
                key = document.dc_id + "_" + document.id;
                String docExt = FileLoader.getDocumentFileName(document);
                int idx;
                if (docExt == null || (idx = docExt.lastIndexOf('.')) == -1) {
                    docExt = "";
                } else {
                    docExt = docExt.substring(idx);
                }
                if (docExt.length() <= 1) {
                    if (document.mime_type != null && document.mime_type.equals("video/mp4")) {
                        docExt = ".mp4";
                    } else {
                        docExt = "";
                    }
                }
                url = key + docExt;
                if (thumbKey != null) {
                    thumbUrl = thumbKey + "." + ext;
                }
            }
            if (imageLocation == thumbLocation) {
                imageLocation = null;
                key = null;
                url = null;
            }
        }

        if (thumbLocation != null) {
            thumbKey = thumbLocation.volume_id + "_" + thumbLocation.local_id;
            thumbUrl = thumbKey + "." + ext;
        }

        String filter = imageReceiver.getFilter();
        String thumbFilter = imageReceiver.getThumbFilter();
        if (key != null && filter != null) {
            key += "@" + filter;
        }
        if (thumbKey != null && thumbFilter != null) {
            thumbKey += "@" + thumbFilter;
        }

        if (httpLocation != null) {
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, ext, thumbLocation, null, thumbFilter, 0, true, thumbSet ? 2 : 1);
            createLoadOperationForImageReceiver(imageReceiver, key, url, ext, null, httpLocation, filter, 0, true, 0);
        } else {
            createLoadOperationForImageReceiver(imageReceiver, thumbKey, thumbUrl, ext, thumbLocation, null, thumbFilter, 0, true, thumbSet ? 2 : 1);
            createLoadOperationForImageReceiver(imageReceiver, key, url, ext, imageLocation, null, filter, imageReceiver.getSize(), saveImageToCache || imageReceiver.getCacheOnly(), 0);
        }
    }
    
    public static String getHttpUrlExtension(String url, String defaultExt) {
        String ext = null;
        int idx = url.lastIndexOf('.');
        if (idx != -1) {
            ext = url.substring(idx + 1);
        }
        if (ext == null || ext.length() == 0 || ext.length() > 4) {
            ext = defaultExt;
        }
        return ext;
    }
    
}
