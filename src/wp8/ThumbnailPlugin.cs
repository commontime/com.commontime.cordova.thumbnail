using System;
using System.Diagnostics;
using System.IO;
using System.IO.IsolatedStorage;
using System.Net;
using System.Windows;
using System.Windows.Media.Imaging;

using Newtonsoft.Json.Linq;

namespace WPCordovaClassLib.Cordova.Commands
{
  public class ThumbnailPlugin : BaseCommand
  {
    public void makeThumbnail(string args)
    {
      string callbackId = "";

      try
      {
        JArray options = JArray.Parse(args);
        string path = (string) options[0];
        int width = (int) options[1];
        int height = (int) options[2];
        int quality = 75;

        try
        {
          quality = (int) options[3];
        }
        catch (Exception)
        {
        }

        callbackId = (string) options[4];

        Debug.WriteLine("Want thumbnail of {0} with maximum dimensions {1}x{2}", path, width, height);

        if (path.StartsWith("http://") || path.StartsWith("https://"))
        {
          BeginDownload(callbackId, new Uri(path), width, height, quality);
        }
        else
        {
          Deployment.Current.Dispatcher.BeginInvoke(() => GetThumbnail(callbackId, path, width, height, quality));
        }
      }
      catch (Exception e)
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message), callbackId);
      }
    }

    private void GetThumbnail(string callbackId, string path, int maxWidth, int maxHeight, int quality)
    {
      try
      {
        using (IsolatedStorageFile userStore = IsolatedStorageFile.GetUserStoreForApplication())
        {
          using (IsolatedStorageFileStream stream = new IsolatedStorageFileStream(path, FileMode.Open, userStore))
          {
            GetThumbnail(callbackId, stream, stream.Length, maxWidth, maxHeight, quality);
          }
        }
      }
      catch (Exception e)
      {
        string message = string.Format("cannot get thumbnail from {0}: {1}", path, e.Message);

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, message));
      }
    }

    private void GetThumbnail(string callbackId, Stream stream, long originalSize, int maxWidth, int maxHeight, int quality)
    {
      BitmapImage bitmap = new BitmapImage();
     
      bitmap.SetSource(stream);
      
      int width = bitmap.PixelWidth;
      int height = bitmap.PixelHeight;

      Debug.WriteLine("Original dimensions are {0}x{1}, with data size {2:N1} KB", width, height, originalSize / 1024.0);

      if (width > maxWidth || height > maxHeight)
      {
        double ratio = Math.Min((double) maxWidth / width, (double) maxHeight / height);

        width = (int) (width * ratio);
        height = (int) (height * ratio);
      }

      WriteableBitmap writeableBitmap = new WriteableBitmap(bitmap);
      string thumbnailData;

      using (MemoryStream memoryStream = new MemoryStream())
      {
        writeableBitmap.SaveJpeg(memoryStream, width, height, 0, quality);

        byte[] data = memoryStream.ToArray();

        Debug.WriteLine("Got thumbnail at {0}x{1} with data size {2:N1} KB", width, height, data.Length / 1024.0);

        thumbnailData = Convert.ToBase64String(data);
      }

      string dataUrl = string.Format("data:image/jpeg;base64,{0}", thumbnailData);

      DispatchCommandResult(new PluginResult(PluginResult.Status.OK, dataUrl), callbackId);
    }

    #region Downloading Images

    private class RequestState
    {
      public RequestState(string callbackId, HttpWebRequest request, int maxWidth, int maxHeight, int quality)
      {
        this.Request = request;
        this.CallbackId = callbackId;
        this.MaxWidth = maxWidth;
        this.MaxHeight = maxHeight;
        this.Quality = quality;
      }

      public HttpWebRequest Request
      {
        get;
        private set;
      }

      public string CallbackId
      {
        get;
        private set;
      }

      public int MaxWidth
      {
        get;
        private set;
      }

      public int MaxHeight
      {
        get;
        private set;
      }

      public int Quality
      {
        get;
        private set;
      }
    }

    private void BeginDownload(string callbackId, Uri uri, int maxWidth, int maxHeight, int quality)
    {
      try
      {
        HttpWebRequest request = (HttpWebRequest) WebRequest.Create(uri);

        request.BeginGetResponse(EndDownload, new RequestState(callbackId, request, maxWidth, maxHeight, quality));
      }
      catch (Exception e)
      {
        string message = string.Format("cannot get thumbnail from {0}: {1}", uri, e.Message);

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message), callbackId);
      }
    }

    private void EndDownload(IAsyncResult result)
    {
      RequestState state = (RequestState) result.AsyncState;

      try
      {
        HttpWebResponse response = (HttpWebResponse) state.Request.EndGetResponse(result);
        Stream inputStream = response.GetResponseStream();
        long originalSize = response.ContentLength;

        Deployment.Current.Dispatcher.BeginInvoke(() =>
        {
          GetThumbnail(state.CallbackId, inputStream, originalSize, state.MaxWidth, state.MaxHeight, state.Quality);
          response.Dispose();
        });
      }
      catch (Exception e)
      {
        string message = string.Format("cannot get thumbnail from {0}: {1}", state.Request.RequestUri, e.Message);

        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message), state.CallbackId);
      }
    }

    #endregion
  }
}