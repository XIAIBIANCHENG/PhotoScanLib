package com.photos.utils;

import android.util.Log;

public class LogUtil {
	public static void e(String tag,String msg){
		Log.e(tag, msg);
	}
	public static void e(String tag,Throwable e){
		e(tag,e.toString());
	}
	
	public static void d(String tag,String msg){
		Log.d(tag, msg);
	}
}
