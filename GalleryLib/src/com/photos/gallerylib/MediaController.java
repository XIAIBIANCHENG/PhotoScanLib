package com.photos.gallerylib;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.photos.utils.AndroidUtilities;
import com.photos.utils.LogUtil;
import com.photos.utils.NotificationCenter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

public class MediaController {
	
	private static volatile MediaController Instance = null;
	public static AlbumEntry allPhotosAlbumEntry;
	private boolean saveToGallery = true;

    public static MediaController getInstance() {
        MediaController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MediaController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MediaController();
                }
            }
        }
        return localInstance;
    }
	public static class PhotoEntry {
        public int bucketId;
        public int imageId;
        public long dateTaken;
        public String path;
        public int orientation;
        public String thumbPath;
        public String imagePath;
        public boolean isVideo;
        public CharSequence caption;

        public PhotoEntry(int bucketId, int imageId, long dateTaken, String path, int orientation, boolean isVideo) {
            this.bucketId = bucketId;
            this.imageId = imageId;
            this.dateTaken = dateTaken;
            this.path = path;
            this.orientation = orientation;
            this.isVideo = isVideo;
        }
    }
	
	public static class AlbumEntry {
        public int bucketId;
        public String bucketName;
        public PhotoEntry coverPhoto;
        public ArrayList<PhotoEntry> photos = new ArrayList<>();
        public HashMap<Integer, PhotoEntry> photosByIds = new HashMap<>();
        public boolean isVideo;

        public AlbumEntry(int bucketId, String bucketName, PhotoEntry coverPhoto, boolean isVideo) {
            this.bucketId = bucketId;
            this.bucketName = bucketName;
            this.coverPhoto = coverPhoto;
            this.isVideo = isVideo;
        }

        public void addPhoto(PhotoEntry photoEntry) {
            photos.add(photoEntry);
            photosByIds.put(photoEntry.imageId, photoEntry);
        }
    }
	
	private static final String[] projectionPhotos = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.ORIENTATION
    };
	
	private static final String[] projectionVideo = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DATE_TAKEN
    };


	
	public static void loadGalleryPhotosAlbums(final int guid) {
        Thread thread = new Thread(new Runnable() {
            @SuppressLint("NewApi")
			@Override
            public void run() {
                final ArrayList<AlbumEntry> albumsSorted = new ArrayList<>();
                final ArrayList<AlbumEntry> videoAlbumsSorted = new ArrayList<>();
                HashMap<Integer, AlbumEntry> albums = new HashMap<>();
                AlbumEntry allPhotosAlbum = null;
                String cameraFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/" + "Camera/";
                Integer cameraAlbumId = null;
                Integer cameraAlbumVideoId = null;

                Cursor cursor = null;
                try {
                    if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 23 && AndroidUtilities.mainContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        cursor = MediaStore.Images.Media.query(AndroidUtilities.mainContext.getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projectionPhotos, null, null, MediaStore.Images.Media.DATE_TAKEN + " DESC");
                        if (cursor != null) {
                            int imageIdColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID);
                            int bucketIdColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_ID);
                            int bucketNameColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME);
                            int dataColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                            int dateColumn = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
                            int orientationColumn = cursor.getColumnIndex(MediaStore.Images.Media.ORIENTATION);

                            while (cursor.moveToNext()) {
                                int imageId = cursor.getInt(imageIdColumn);
                                int bucketId = cursor.getInt(bucketIdColumn);
                                String bucketName = cursor.getString(bucketNameColumn);
                                String path = cursor.getString(dataColumn);
                                long dateTaken = cursor.getLong(dateColumn);
                                int orientation = cursor.getInt(orientationColumn);

                                if (path == null || path.length() == 0) {
                                    continue;
                                }

                                PhotoEntry photoEntry = new PhotoEntry(bucketId, imageId, dateTaken, path, orientation, false);

                                if (allPhotosAlbum == null) {
                                    allPhotosAlbum = new AlbumEntry(0,"All Photos", photoEntry, false);
                                    albumsSorted.add(0, allPhotosAlbum);
                                }
                                if (allPhotosAlbum != null) {
                                    allPhotosAlbum.addPhoto(photoEntry);
                                }

                                AlbumEntry albumEntry = albums.get(bucketId);
                                if (albumEntry == null) {
                                    albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry, false);
                                    albums.put(bucketId, albumEntry);
                                    if (cameraAlbumId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                        albumsSorted.add(0, albumEntry);
                                        cameraAlbumId = bucketId;
                                    } else {
                                        albumsSorted.add(albumEntry);
                                    }
                                }

                                albumEntry.addPhoto(photoEntry);
                            }
                        }
                    }
                } catch (Throwable e) {
                    LogUtil.e("tmessages", e);
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Exception e) {
                            LogUtil.e("tmessages", e);
                        }
                    }
                }

                try {
                    if (Build.VERSION.SDK_INT < 23 || Build.VERSION.SDK_INT >= 23 &&AndroidUtilities.mainContext.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        albums.clear();
                        AlbumEntry allVideosAlbum = null;
                        cursor = MediaStore.Images.Media.query(AndroidUtilities.mainContext.getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projectionVideo, null, null, MediaStore.Video.Media.DATE_TAKEN + " DESC");
                        if (cursor != null) {
                            int imageIdColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                            int bucketIdColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID);
                            int bucketNameColumn = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
                            int dataColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                            int dateColumn = cursor.getColumnIndex(MediaStore.Video.Media.DATE_TAKEN);

                            while (cursor.moveToNext()) {
                                int imageId = cursor.getInt(imageIdColumn);
                                int bucketId = cursor.getInt(bucketIdColumn);
                                String bucketName = cursor.getString(bucketNameColumn);
                                String path = cursor.getString(dataColumn);
                                long dateTaken = cursor.getLong(dateColumn);

                                if (path == null || path.length() == 0) {
                                    continue;
                                }

                                PhotoEntry photoEntry = new PhotoEntry(bucketId, imageId, dateTaken, path, 0, true);

                                if (allVideosAlbum == null) {
                                    allVideosAlbum = new AlbumEntry(0, "All Photos", photoEntry, true);
                                    videoAlbumsSorted.add(0, allVideosAlbum);
                                }
                                if (allVideosAlbum != null) {
                                    allVideosAlbum.addPhoto(photoEntry);
                                }

                                AlbumEntry albumEntry = albums.get(bucketId);
                                if (albumEntry == null) {
                                    albumEntry = new AlbumEntry(bucketId, bucketName, photoEntry, true);
                                    albums.put(bucketId, albumEntry);
                                    if (cameraAlbumVideoId == null && cameraFolder != null && path != null && path.startsWith(cameraFolder)) {
                                        videoAlbumsSorted.add(0, albumEntry);
                                        cameraAlbumVideoId = bucketId;
                                    } else {
                                        videoAlbumsSorted.add(albumEntry);
                                    }
                                }

                                albumEntry.addPhoto(photoEntry);
                            }
                        }
                    }
                } catch (Throwable e) {
                    LogUtil.e("tmessages", e);
                } finally {
                    if (cursor != null) {
                        try {
                            cursor.close();
                        } catch (Exception e) {
                            LogUtil.e("tmessages", e);
                        }
                    }
                }

                final Integer cameraAlbumIdFinal = cameraAlbumId;
                final Integer cameraAlbumVideoIdFinal = cameraAlbumVideoId;
                final AlbumEntry allPhotosAlbumFinal = allPhotosAlbum;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        allPhotosAlbumEntry = allPhotosAlbumFinal;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.albumsDidLoaded, guid, albumsSorted, cameraAlbumIdFinal, videoAlbumsSorted, cameraAlbumVideoIdFinal);
                    }
                });
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }
	
	public void checkSaveToGalleryFiles() {
        try {
            File telegramPath = new File(Environment.getExternalStorageDirectory(), "Telegram");
            File imagePath = new File(telegramPath, "Telegram Images");
            imagePath.mkdir();
            File videoPath = new File(telegramPath, "Telegram Video");
            videoPath.mkdir();

            if (saveToGallery) {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").delete();
                }
                if (videoPath.isDirectory()) {
                    new File(videoPath, ".nomedia").delete();
                }
            } else {
                if (imagePath.isDirectory()) {
                    new File(imagePath, ".nomedia").createNewFile();
                }
                if (videoPath.isDirectory()) {
                    new File(videoPath, ".nomedia").createNewFile();
                }
            }
        } catch (Exception e) {
            LogUtil.e("tmessages", e);
        }
    }
}
