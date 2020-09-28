package com.sinovoice.example;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import android.widget.TextView;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;


public class HciCloudHelper {
    private static final String TAG = HciCloudHelper.class.getSimpleName();
    private static TextView mView = null;
    private static Context mContext = null;


    public static void setContext(Context context){
    	mContext = context;
    }


	/**
	 * 拷贝assets/data目录下的文件到指定目录
	 * 
	 * @param context
	 * 		    上下文
	 * @param dataPath
	 * 		    指定目录
	 * @return
	 */
	public static void copyAssetsFiles(Context context, String dataPath) {
		// 没有文件夹，则创建
		if (!new File(dataPath).exists()) {
			new File(dataPath).mkdirs();
		}

		AssetManager assetMgr = context.getResources().getAssets();
		try {
			String[] filesList = assetMgr.list("data");
			LoopFiles(filesList, assetMgr, "data" + File.separator, dataPath + File.separator);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void LoopFiles(String []filesList, AssetManager assetMgr, String srcPath, String destPath)
	{
		Log.i(TAG, "srcPath="+srcPath);
		Log.i(TAG, "destPath="+destPath);
		for(String string : filesList)
		{
			try {
				String[] file = assetMgr.list(srcPath + string);
				if (file.length == 0) {
					copyAssetFile(assetMgr, srcPath + string, destPath + string);
				} else {
					if (!new File(destPath + string).exists()) {
						new File(destPath + string).mkdirs();
					}

					LoopFiles(file, assetMgr,
							srcPath + string + File.separator, destPath
									+ string + File.separator);
				}

			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void copyAssetFile(AssetManager assetMgr, String src,
			String dst) {
		if (assetMgr == null) {
			throw new NullPointerException("Method param assetMgr is null.");
		}

		if (src == null) {
			throw new NullPointerException("Method param src is null.");
		}

		if (dst == null) {
			throw new NullPointerException("Method param dst is null.");
		}

		InputStream is = null;
		DataInputStream dis = null;
		FileOutputStream fos = null;
		DataOutputStream dos = null;
		try {
			is = assetMgr.open(src, AssetManager.ACCESS_RANDOM);
			dis = new DataInputStream(is);

			File file = new File(dst);
			if (file.exists()) {
				file.delete();
				//return;
			}
			file.createNewFile();

			fos = new FileOutputStream(file);
			dos = new DataOutputStream(fos);
			byte[] buffer = new byte[1024];

			int len = 0;
			while ((len = dis.read(buffer, 0, buffer.length)) != -1) {
				dos.write(buffer, 0, len);
				dos.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (dis != null) {
					dis.close();
					dis = null;
				}

				if (is != null) {
					is.close();
					is = null;
				}
				if (dos != null) {
					dos.close();
					dos = null;
				}

				if (fos != null) {
					fos.close();
					fos = null;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void setTextView(TextView view){
    	mView = view;
    }
	
	/**
	 * 显示消息
	 * 
	 * @param message
	 * @return
	 */
	public static void ShowMessage(String message){
		 mView.append(message + "\n");
	}
	
}
