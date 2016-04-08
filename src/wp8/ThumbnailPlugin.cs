using System;
using System.Diagnostics;
using System.IO;
using System.IO.IsolatedStorage;
using System.Windows;
using System.Windows.Media.Imaging;

using Newtonsoft.Json.Linq;

namespace WPCordovaClassLib.Cordova.Commands
{
  public class ThumbnailPlugin : BaseCommand
  {
    public void makeThumbnail(string args)
    {
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

        Deployment.Current.Dispatcher.BeginInvoke(() => GetThumbnail(CurrentCommandCallbackId, path, width, height, quality));
      }
      catch (Exception e)
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }

    private void GetThumbnail(string callbackId, string path, int maxWidth, int maxHeight, int quality)
    {
      try
      {
        BitmapImage bitmap = new BitmapImage();
        long originalSize = 0;

        using (IsolatedStorageFile userStore = IsolatedStorageFile.GetUserStoreForApplication())
        {
          using (IsolatedStorageFileStream stream = new IsolatedStorageFileStream(path, FileMode.Open, userStore))
          {
            originalSize = stream.Length;
            bitmap.SetSource(stream);
          }
        }

        int width = bitmap.PixelWidth;
        int height = bitmap.PixelHeight;

        Debug.WriteLine("Want thumbnail of {0} with maximum dimensions {1}x{2}", path, maxWidth, maxHeight);
        Debug.WriteLine("Original dimensions are {0}x{1}, with data size {2:N1} KB", width, height, originalSize / 1024.0);

        if (width > maxWidth || height > maxHeight)
        {
          double ratio = Math.Min((double) maxWidth / width, (double) maxHeight / height);

          width = (int) (width * ratio);
          height = (int) (height * ratio);
        }

        WriteableBitmap writeableBitmap = new WriteableBitmap(bitmap);
        string thumbnailData;

        using (MemoryStream stream = new MemoryStream())
        {
          writeableBitmap.SaveJpeg(stream, width, height, 0, quality);

          byte[] data = stream.ToArray();

          Debug.WriteLine("Got thumbnail at {0}x{1} with data size {2:N1} KB", width, height, data.Length / 1024.0);

          thumbnailData = Convert.ToBase64String(data);
        }

        string dataUrl = string.Format("data:image/jpeg;base64,{0}", thumbnailData);

        DispatchCommandResult(new PluginResult(PluginResult.Status.OK, dataUrl), callbackId);
      }
      catch (Exception e)
      {
        DispatchCommandResult(new PluginResult(PluginResult.Status.ERROR, e.Message));
      }
    }
  }
}