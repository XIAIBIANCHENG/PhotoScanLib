package com.photos.test;

import java.util.ArrayList;
import java.util.HashMap;

import com.photos.gallerylib.MediaController;
import com.photos.gallerylib.NativeLoader;
import com.photos.gallerylib.PhotoAttachPhotoCell;
import com.photos.gallerylib.R;
import com.photos.gallerylib.R.id;
import com.photos.utils.AndroidUtilities;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;
import com.photos.utils.NotificationCenter;

public class MainActivity extends Activity implements NotificationCenter.NotificationCenterDelegate{
	private  RecyclerView recyclerView;
	private LinearLayoutManager attachPhotoLayoutManager;
    private PhotoAttachAdapter photoAttachAdapter;
    private ArrayList<PhotoAttachAdapter.Holder> viewsCache = new ArrayList<>(8);
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		NativeLoader.initNativeLibs(getApplicationContext());
		AndroidUtilities.init(getApplicationContext());
		setContentView(R.layout.activity_main);
		
		NotificationCenter.getInstance().addObserver(this, NotificationCenter.albumsDidLoaded);
		recyclerView=(RecyclerView) findViewById(id.id_recyclerview);
		recyclerView.setVerticalScrollBarEnabled(true);
		recyclerView.setAdapter(photoAttachAdapter = new PhotoAttachAdapter(this));
		recyclerView.setClipToPadding(false);
		recyclerView.setPadding(dp(8), 0, dp(8), 0);
		recyclerView.setItemAnimator(null);
		recyclerView.setLayoutAnimation(null);
		recyclerView.setOverScrollMode(RecyclerView.OVER_SCROLL_NEVER);
		attachPhotoLayoutManager = new LinearLayoutManager(this) {
            @Override
            public boolean supportsPredictiveItemAnimations() {
                return false;
            }
        };
        attachPhotoLayoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(attachPhotoLayoutManager);
        MediaController.loadGalleryPhotosAlbums(0);
	}
	
	private int dp(int p){
		float density = getResources().getDisplayMetrics().density;
		return (int) Math.ceil(density * p);
	}
	
	
	
	@SuppressWarnings("rawtypes")
	public class PhotoAttachAdapter extends RecyclerView.Adapter{
		private Context mContext;
		private HashMap<Integer, MediaController.PhotoEntry> selectedPhotos = new HashMap<>();
		public PhotoAttachAdapter(Context context) {
            mContext = context;
        }
		@Override
		public int getItemCount() {
			int c=(MediaController.allPhotosAlbumEntry != null ? MediaController.allPhotosAlbumEntry.photos.size() : 0);
			return c;
		}

		@Override
		public void onBindViewHolder(ViewHolder holder, int position) {
			PhotoAttachPhotoCell cell = (PhotoAttachPhotoCell) holder.itemView;
            MediaController.PhotoEntry photoEntry = MediaController.allPhotosAlbumEntry.photos.get(position);
            cell.setPhotoEntry(photoEntry, position == MediaController.allPhotosAlbumEntry.photos.size() - 1);
            cell.setChecked(selectedPhotos.containsKey(photoEntry.imageId), false);
            cell.getImageView().setTag(position);
            cell.setTag(position);
		}

		@Override
		public ViewHolder onCreateViewHolder(ViewGroup arg0, int arg1) {
			 Holder holder;
	            if (!viewsCache.isEmpty()) {
	                holder = viewsCache.get(0);
	                viewsCache.remove(0);
	            } else {
	                holder = createHolder();
	            }
	            return holder;
		}
		
		public Holder createHolder() {
            PhotoAttachPhotoCell cell = new PhotoAttachPhotoCell(mContext);
            cell.setDelegate(new PhotoAttachPhotoCell.PhotoAttachPhotoCellDelegate() {
                @Override
                public void onCheckClick(PhotoAttachPhotoCell v) {
                    MediaController.PhotoEntry photoEntry = v.getPhotoEntry();
                    if (selectedPhotos.containsKey(photoEntry.imageId)) {
                        selectedPhotos.remove(photoEntry.imageId);
                        v.setChecked(false, true);
                        photoEntry.imagePath = null;
                        photoEntry.thumbPath = null;
                        v.setPhotoEntry(photoEntry, (Integer) v.getTag() == MediaController.allPhotosAlbumEntry.photos.size() - 1);
                    } else {
                        selectedPhotos.put(photoEntry.imageId, photoEntry);
                        v.setChecked(true, true);
                    }
//                    updatePhotosButton();
                }
            });
            return new Holder(cell);
        }
		
		private class Holder extends RecyclerView.ViewHolder {

            public Holder(View itemView) {
                super(itemView);
            }
        }
		
	}



	@Override
	public void didReceivedNotification(int id, Object... args) {
		 if (id == NotificationCenter.albumsDidLoaded) {
	            if (photoAttachAdapter != null) {
//	                loading = false;
//	                progressView.showTextView();
	                photoAttachAdapter.notifyDataSetChanged();
	            }
	        }
	}
}
