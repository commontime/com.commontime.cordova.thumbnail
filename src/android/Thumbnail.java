package com.commontime.plugin;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class Thumbnail extends CordovaPlugin {

    private static final int MAX_IMAGE_DECODING_SIZE = 2000;
    private static final String LOG_TAG = "Thumbnail";

    private JSONArray savedArgs;
    private int savedMaxWidth;
    private int savedMaxHeight;
    private int savedQuality;
    private Map<String, CallbackContext> callbackContextList;
    private boolean wasDecrypted;
    private static final String DECRYPT_FILE_ERROR_MSG = "Failed to make thumbnail due to file decryption";
    private static final String DECRYPT_FILE_MSG_ID = "DECRYPT_FILE";
    private static final String DECRYPT_FILE_CALLBACK_MSG_ID = "DECRYPTION_RESPONSE";
    private static final String DECRYPT_FILE_URI_KEY = "uri";
    private static final String DECRYPT_FILE_CALLBACK_KEY = "cb";
    private static final String DECRYPT_TARGET_KEY = "target";
    private static final String ENCRYPT_DECRYPT_REQUEST_ID_KEY = "encryptDecryptRequestId";
    private static final String ENCRYPTED_FILE_EXTENSION = ".encrypted";

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext)
    {
        if (callbackContextList == null)
            callbackContextList = new ArrayMap<String, CallbackContext>();

        cordova.getThreadPool().execute(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    getThumbnail(args.getString(0), args.getInt(1), args.getInt(2), args.getInt(3), args, callbackContext);
                }
                catch (Exception e)
                {
                    callbackContext.error(e.getMessage());
                }
            }
        });

        return true;
    }

    @Override
    public Object onMessage(String id, Object data) {
        if (id.equals(DECRYPT_FILE_CALLBACK_MSG_ID)) {
            if (data != null) {
                try {
                    JSONObject jsonData = (JSONObject) data;
                    String requestId = ((JSONObject) data).getString(ENCRYPT_DECRYPT_REQUEST_ID_KEY);
                    CallbackContext callbackContext = callbackContextList.get(requestId);
                    callbackContextList.remove(requestId);
                    getThumbnail(jsonData.getString(DECRYPT_FILE_URI_KEY), savedMaxWidth, savedMaxHeight, savedQuality, savedArgs, callbackContext);
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e(LOG_TAG, DECRYPT_FILE_ERROR_MSG);
                }
            } else {
                Log.e(LOG_TAG, DECRYPT_FILE_ERROR_MSG);
            }
        }
        return super.onMessage(id, data);
    }

    private void getThumbnail(String path, int maxWidth, int maxHeight, int quality, JSONArray args, CallbackContext callbackContext) throws JSONException
    {
        if (path.contains(ENCRYPTED_FILE_EXTENSION))
        {
            if (webView.getPluginManager().getPlugin("FileEncryption") == null)
            {
                callbackContext.error("Unable to decrypt to make a thumbnail for the file at '" + path + "'. File encryption plugin not present.");
            }

            String decryptRequestId = UUID.randomUUID().toString();

            callbackContextList.put(decryptRequestId, callbackContext);

            savedArgs = args;
            savedMaxWidth = maxWidth;
            savedMaxHeight = maxHeight;
            savedQuality = quality;
            wasDecrypted = true;
            JSONObject data = new JSONObject();
            data.put(ENCRYPT_DECRYPT_REQUEST_ID_KEY, decryptRequestId);
            data.put(DECRYPT_FILE_URI_KEY, path);
            data.put(DECRYPT_TARGET_KEY, cordova.getActivity().getCacheDir().getAbsolutePath());
            data.put(DECRYPT_FILE_CALLBACK_KEY, DECRYPT_FILE_CALLBACK_MSG_ID);
            webView.getPluginManager().postMessage(DECRYPT_FILE_MSG_ID, data);
            return;
        }

        if(path.contains("cdvfile://"))
        {
            CordovaResourceApi resourceApi = webView.getResourceApi();
            Uri fileURL = resourceApi.remapUri(Uri.parse(path));
            path = fileURL.getPath();
        }
        else
        {
            path = path.replace("file://", "");
            path = path.replace("file:", "");
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        Bitmap original = null;

        if(path.contains("http://"))
        {
            try
            {
                path = path.replace(" ", "%20");

                URL url = new URL(path);

                //Decode image size
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;

                BitmapFactory.decodeStream(url.openConnection().getInputStream(), null, o);

                int scale = 1;
                if (o.outHeight > MAX_IMAGE_DECODING_SIZE || o.outWidth > MAX_IMAGE_DECODING_SIZE) {
                    scale = (int)Math.pow(2, (int) Math.ceil(Math.log(MAX_IMAGE_DECODING_SIZE /
                            (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
                }

                //Decode with inSampleSize
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;

                original = BitmapFactory.decodeStream(url.openConnection().getInputStream(), null, o2);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }
        else
        {
            try
            {
                original = getBitmapFromStorage(path);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        if(original == null)
        {
            try
            {
                path = path.replace("/android_asset/", "");
                if(!path.contains("www/"))
                {
                    path = "www/" + path;
                }
                original = getBitmapFromAsset(cordova.getActivity(), path);
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
        }

        if (original == null) {
            callbackContext.error("thumbnail error: unable to open image at " + path);
            return;
        }

        String base64String = getBase64String(original, options, path, maxWidth, maxHeight, quality);

        if (wasDecrypted) {
            File fdelete = new File(path);
            if (fdelete.exists()) {
                if (fdelete.delete()) {
                    Log.d(LOG_TAG, "file Deleted : " + path);
                } else {
                    Log.d(LOG_TAG, "file not Deleted : " + path);
                }
            }
            wasDecrypted = false;
        }

        if (base64String == null)
            callbackContext.error("thumbnail error: unable to process image at " + path);
        else
            callbackContext.success(String.format("data:%s;base64,%s", getMimeType(path), base64String));
    }

    private String getBase64String(Bitmap original, BitmapFactory.Options options, String path, int maxWidth, int maxHeight, int quality)
    {
        final int width = options.outWidth != 0 ? options.outWidth : original.getWidth();
        final int height = options.outHeight != 0 ? options.outHeight : original.getHeight();
        float ratio = 1;
        if (width > maxWidth || height > maxHeight) {
            final float widthRatio = (float)maxWidth/(float)width;
            final float heightRatio = (float)maxHeight/(float)height;
            ratio = Math.min(widthRatio, heightRatio);
        }

        options.inSampleSize = (int) (1/ratio);
        options.inJustDecodeBounds = false;

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
            return null;
        }

        Bitmap.CompressFormat compressFormat;

        if(mimeType.equals("image/png"))
            compressFormat = Bitmap.CompressFormat.PNG;
        else
            compressFormat = Bitmap.CompressFormat.JPEG;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        thumb.compress(compressFormat, quality, output);
        thumb.recycle();

        String base64String = Base64.encodeToString(output.toByteArray(), Base64.DEFAULT);
        try {
            output.close();
        } catch (IOException e) {
            return null;
        }

        return base64String;
    }

    private Bitmap getBitmapFromAsset(Context context, String path) throws Exception
    {
        AssetManager assetManager = context.getAssets();
        InputStream istr = assetManager.open(path);

        Bitmap b = null;

        //Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;

        BitmapFactory.decodeStream(istr, null, o);

        istr.close();

        int scale = 1;
        if (o.outHeight > MAX_IMAGE_DECODING_SIZE || o.outWidth > MAX_IMAGE_DECODING_SIZE) {
            scale = (int)Math.pow(2, (int) Math.ceil(Math.log(MAX_IMAGE_DECODING_SIZE /
                    (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
        }

        //Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        istr = assetManager.open(path);
        b = BitmapFactory.decodeStream(istr, null, o2);

        return b;
    }

    private Bitmap getBitmapFromStorage(String path) throws Exception
    {
        File f=new File(path);

        Bitmap b = null;

        //Decode image size
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;

        FileInputStream fis = new FileInputStream(f);
        BitmapFactory.decodeStream(fis, null, o);
        fis.close();

        int scale = 1;
        if (o.outHeight > MAX_IMAGE_DECODING_SIZE || o.outWidth > MAX_IMAGE_DECODING_SIZE) {
            scale = (int)Math.pow(2, (int) Math.ceil(Math.log(MAX_IMAGE_DECODING_SIZE /
                    (double) Math.max(o.outHeight, o.outWidth)) / Math.log(0.5)));
        }

        //Decode with inSampleSize
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = scale;
        fis = new FileInputStream(f);
        b = BitmapFactory.decodeStream(fis, null, o2);
        fis.close();

        return b;
    }

    public static String getMimeType(String url)
    {
        try
        {
            String extension = url;
            int lastDot = extension.lastIndexOf('.');
            if (lastDot != -1) {
                extension = extension.substring(lastDot + 1);
            }
            extension = extension.toLowerCase(Locale.getDefault());
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }
}