//package com.photos.gallerylib;
//
//import java.io.File;
//import java.io.RandomAccessFile;
//import java.util.ArrayList;
//
//import com.photos.nativeobject.TLObject;
//import com.photos.nativeobject.TLRPC;
//import com.photos.utils.LogUtil;
//import com.photos.utils.Utilities;
//
//public class FileLoadOperation {
//	 private static class RequestInfo {
//	        private int requestToken;
//	        private int offset;
////	        private TLRPC.TL_upload_file response;
//	    }
//
//	    private final static int stateIdle = 0;
//	    private final static int stateDownloading = 1;
//	    private final static int stateFailed = 2;
//	    private final static int stateFinished = 3;
//
//	    private final static int downloadChunkSize = 1024 * 32;
//	    private final static int downloadChunkSizeBig = 1024 * 128;
//	    private final static int maxDownloadRequests = 4;
//	    private final static int maxDownloadRequestsBig = 2;
//	    private final static int bigFileSizeFrom = 1024 * 1024;
//
//	    private int datacenter_id;
//	    private TLRPC.InputFileLocation location;
//	    private volatile int state = stateIdle;
//	    private int downloadedBytes;
//	    private int totalBytesCount;
//	    private FileLoadOperationDelegate delegate;
//	    private byte[] key;
//	    private byte[] iv;
//	    private int currentDownloadChunkSize;
//	    private int currentMaxDownloadRequests;
//	    private int requestsCount;
//	    private int renameRetryCount;
//
//	    private int nextDownloadOffset;
//	    private ArrayList<RequestInfo> requestInfos;
//	    private ArrayList<RequestInfo> delayedRequestInfos;
//
//	    private File cacheFileTemp;
//	    private File cacheFileFinal;
//	    private File cacheIvTemp;
//
//	    private String ext;
//	    private RandomAccessFile fileOutputStream;
//	    private RandomAccessFile fiv;
//	    private File storePath;
//	    private File tempPath;
//	    private boolean isForceRequest;
//
//	    public interface FileLoadOperationDelegate {
//	        void didFinishLoadingFile(FileLoadOperation operation, File finalFile);
//	        void didFailedLoadingFile(FileLoadOperation operation, int state);
//	        void didChangedLoadProgress(FileLoadOperation operation, float progress);
//	    }
//
//	    public FileLoadOperation(TLRPC.FileLocation photoLocation, String extension, int size) {
//	        if (photoLocation instanceof TLRPC.TL_fileEncryptedLocation) {
//	            location = new TLRPC.TL_inputEncryptedFileLocation();
//	            location.id = photoLocation.volume_id;
//	            location.volume_id = photoLocation.volume_id;
//	            location.access_hash = photoLocation.secret;
//	            location.local_id = photoLocation.local_id;
//	            iv = new byte[32];
//	            System.arraycopy(photoLocation.iv, 0, iv, 0, iv.length);
//	            key = photoLocation.key;
//	            datacenter_id = photoLocation.dc_id;
//	        } else if (photoLocation instanceof TLRPC.TL_fileLocation) {
//	            location = new TLRPC.TL_inputFileLocation();
//	            location.volume_id = photoLocation.volume_id;
//	            location.secret = photoLocation.secret;
//	            location.local_id = photoLocation.local_id;
//	            datacenter_id = photoLocation.dc_id;
//	        }
//	        totalBytesCount = size;
//	        ext = extension != null ? extension : "jpg";
//	    }
//
//	    public FileLoadOperation(TLRPC.Document documentLocation) {
//	        try {
//	            if (documentLocation instanceof TLRPC.TL_documentEncrypted) {
//	                location = new TLRPC.TL_inputEncryptedFileLocation();
//	                location.id = documentLocation.id;
//	                location.access_hash = documentLocation.access_hash;
//	                datacenter_id = documentLocation.dc_id;
//	                iv = new byte[32];
//	                System.arraycopy(documentLocation.iv, 0, iv, 0, iv.length);
//	                key = documentLocation.key;
//	            } else if (documentLocation instanceof TLRPC.TL_document) {
//	                location = new TLRPC.TL_inputDocumentFileLocation();
//	                location.id = documentLocation.id;
//	                location.access_hash = documentLocation.access_hash;
//	                datacenter_id = documentLocation.dc_id;
//	            }
//	            if (totalBytesCount <= 0) {
//	                totalBytesCount = documentLocation.size;
//	            }
//	            ext = FileLoader.getDocumentFileName(documentLocation);
//	            int idx;
//	            if (ext == null || (idx = ext.lastIndexOf('.')) == -1) {
//	                ext = "";
//	            } else {
//	                ext = ext.substring(idx);
//	            }
//	            if (ext.length() <= 1) {
//	                if (documentLocation.mime_type != null) {
//	                    switch (documentLocation.mime_type) {
//	                        case "video/mp4":
//	                            ext = ".mp4";
//	                            break;
//	                        case "audio/ogg":
//	                            ext = ".ogg";
//	                            break;
//	                        default:
//	                            ext = "";
//	                            break;
//	                    }
//	                } else {
//	                    ext = "";
//	                }
//	            }
//	        } catch (Exception e) {
//	            LogUtil.e("tmessages", e);
//	            state = stateFailed;
////	            cleanup();
//	            Utilities.stageQueue.postRunnable(new Runnable() {
//	                @Override
//	                public void run() {
//	                    delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
//	                }
//	            });
//	        }
//	    }
//
//	    public void setForceRequest(boolean forceRequest) {
//	        isForceRequest = forceRequest;
//	    }
//
//	    public boolean isForceRequest() {
//	        return isForceRequest;
//	    }
//
//	    public void setPaths(File store, File temp) {
//	        storePath = store;
//	        tempPath = temp;
//	    }
//
//
//
//
//	    private void onFinishLoadingFile() throws Exception {
//	        if (state != stateDownloading) {
//	            return;
//	        }
//	        state = stateFinished;
////	        cleanup();
//	        if (cacheIvTemp != null) {
//	            cacheIvTemp.delete();
//	            cacheIvTemp = null;
//	        }
//	        if (cacheFileTemp != null) {
//	            boolean renameResult = cacheFileTemp.renameTo(cacheFileFinal);
//	            if (!renameResult) {
//	                renameRetryCount++;
//	                if (renameRetryCount < 3) {
//	                    state = stateDownloading;
//	                    Utilities.stageQueue.postRunnable(new Runnable() {
//	                        @Override
//	                        public void run() {
//	                            try {
//	                                onFinishLoadingFile();
//	                            } catch (Exception e) {
//	                                delegate.didFailedLoadingFile(FileLoadOperation.this, 0);
//	                            }
//	                        }
//	                    }, 200);
//	                    return;
//	                }
//	                cacheFileFinal = cacheFileTemp;
//	            }
//	        }
//	        delegate.didFinishLoadingFile(FileLoadOperation.this, cacheFileFinal);
//	    }
//
//	    
//
//	    public void setDelegate(FileLoadOperationDelegate delegate) {
//	        this.delegate = delegate;
//	    }
//}
