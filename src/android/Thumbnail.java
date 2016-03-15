package com.commontime.plugin;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;

public class Thumbnail extends CordovaPlugin {

    @Override
    public boolean execute(String action, final JSONArray data, final CallbackContext callbackContext) throws JSONException
    {
        cordova.getThreadPool().execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    getThumbnail(data.getString(0), data.getInt(1), data.getInt(2), data.getInt(3), callbackContext);
                }
                catch (Exception e)
                {
                    callbackContext.error(e.getMessage());
                }
            }
        });

        return true;
    }

    private void getThumbnail(String path, int maxWidth, int maxHeight, int quality, CallbackContext callbackContext)
    {
        if(path.contains("cdvfile://"))
        {
            CordovaResourceApi resourceApi = webView.getResourceApi();
            Uri fileURL = resourceApi.remapUri(Uri.parse(path));
            path = fileURL.getPath();
        }
        else
        {
            path = path.replace("file://", "");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        if(path.contains("http://"))
        {
            try
            {
                URL url = new URL(path);
                BitmapFactory.decodeStream(url.openConnection().getInputStream(), null, options);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            BitmapFactory.decodeFile(path, options);
        }

        final int width = options.outWidth;
        final int height = options.outHeight;
        float ratio = 1;
        if (width > maxWidth || height > maxHeight) {
            final float widthRatio = (float)maxWidth/(float)width;
            final float heightRatio = (float)maxHeight/(float)height;
            ratio = Math.min(widthRatio, heightRatio);
        }

        options.inSampleSize = (int) (1/ratio);
        options.inJustDecodeBounds = false;

        Bitmap original = null;

        if(path.contains("http://"))
        {
            try
            {
                URL url = new URL(path);
                original = BitmapFactory.decodeStream(url.openConnection().getInputStream());
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            original = BitmapFactory.decodeFile(path, options);
        }

        if (original == null) {
            callbackContext.error("thumbnail error: unable to open image at " + path);
            return;
        }

        int thumbWidth = Math.round(width * ratio);
        int thumbHeight = Math.round(height * ratio);

        Bitmap thumb = null;
        try
        {
            thumb = Bitmap.createScaledBitmap(original, thumbWidth, thumbHeight, true);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

        if (thumb != original){
            original.recycle();
        }

        String mimeType = getMimeType(path);

        if (TextUtils.isEmpty(mimeType)) {
            callbackContext.error("thumbnail error: unable to open image at " + path);
            return;
        }

        Bitmap.CompressFormat compressFormat = null;

        if(mimeType.equals("image/png"))
            compressFormat = Bitmap.CompressFormat.PNG;
        else
            compressFormat = Bitmap.CompressFormat.JPEG;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        thumb.compress(compressFormat, quality, output);
        thumb.recycle();

        String base64 = Base64.encodeToString(output.toByteArray(), Base64.DEFAULT);
        try {
            output.close();
        } catch (IOException e) {
            callbackContext.error("thumbnail error: error closing output stream");
            return;
        }

        callbackContext.success(String.format("data:%s;base64,%s", mimeType, base64));
    }

    public static String getMimeType(String url)
    {
        try
        {
            String type = null;
            String extension = MimeTypeMap.getFileExtensionFromUrl(url);
            if (extension != null) {
                type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            }
            return type;
        }
        catch(Exception e)
        {
            return null;
        }
    }
}