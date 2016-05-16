package com.photos.utils;

import android.widget.FrameLayout;

public class LayoutHelper {
	private static int getSize(float size) {
        return (int) (size < 0 ? size : AndroidUtilities.dp(size));
    }
	 public static FrameLayout.LayoutParams createFrame(int width, float height, int gravity, float leftMargin, float topMargin, float rightMargin, float bottomMargin) {
	        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
	        layoutParams.setMargins(AndroidUtilities.dp(leftMargin), AndroidUtilities.dp(topMargin), AndroidUtilities.dp(rightMargin), AndroidUtilities.dp(bottomMargin));
	        return layoutParams;
	    }

	    public static FrameLayout.LayoutParams createFrame(int width, int height, int gravity) {
	        return new FrameLayout.LayoutParams(getSize(width), getSize(height), gravity);
	    }

	    public static FrameLayout.LayoutParams createFrame(int width, float height) {
	        return new FrameLayout.LayoutParams(getSize(width), getSize(height));
	    }
}
