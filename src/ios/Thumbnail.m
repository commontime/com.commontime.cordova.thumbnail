#import "Thumbnail.h"
#import "CDVFile.h"
#import "NSData+Base64.h"

@implementation Thumbnail

- (void) makeThumbnail: (CDVInvokedUrlCommand*) command
{
  dispatch_async(dispatch_get_global_queue( DISPATCH_QUEUE_PRIORITY_LOW, 0), ^{
    @try
    {
      NSString* filePath = [command.arguments objectAtIndex: 0];
      
      if([filePath containsString:@"cdvfile://"])
      {
        CDVFile *filePlugin = [[CDVFile alloc] init];
        
        [filePlugin pluginInitialize];
        
        CDVFilesystemURL* url = [CDVFilesystemURL fileSystemURLWithString:filePath];
        
        filePath= [filePlugin filesystemPathForURL:url];
      }
      
      if ([filePath length] == 0)
      {
        dispatch_async(dispatch_get_main_queue(), ^{
          CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                            messageAsString: @"no file path"];

          [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        });
        
        return;
      }
      
      NSInteger maxWidth = [[command.arguments objectAtIndex: 1] integerValue];
      NSInteger maxHeight = [[command.arguments objectAtIndex: 2] integerValue];
      NSInteger quality = [[command.arguments objectAtIndex: 3] integerValue];
      NSData* originalData;
      
      if ([filePath containsString: @"cdvfile://"])
      {
      }
      else if (![filePath containsString: @"http://"] && ![[filePath lowercaseString] hasPrefix: @"file://"])
      {
        if ([filePath characterAtIndex: 0] == '/')
        {
          filePath = [NSString stringWithFormat: @"file://%@", filePath];
        }
        else
        {
          filePath = [NSString stringWithFormat: @"file:///%@", filePath];
        }
      }
      
      NSURL* fileURL = [NSURL URLWithString: [filePath stringByReplacingOccurrencesOfString: @" " withString: @"%20"]];
      
      originalData = [NSData dataWithContentsOfURL: fileURL];
      
      if (!originalData)
      {
        NSString *appFolderPath = [[NSBundle mainBundle] resourcePath];
        NSString *adjustedFilePath = [filePath stringByReplacingOccurrencesOfString: @"file:///" withString: @""];
        NSString *finalPath = [NSString stringWithFormat :@"file://%@/www/%@", appFolderPath, adjustedFilePath];
     
        originalData = [NSData dataWithContentsOfURL: [NSURL URLWithString: finalPath]];
      }
      
      if (!originalData)
      {
        NSString* message = [NSString stringWithFormat: @"Cannot find image file %@", filePath];
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                          messageAsString: message];
    
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        
        return;
      }
      
      UIImage* original = [UIImage imageWithData: originalData];
      CGFloat width = original.size.width;
      CGFloat height = original.size.height;
      CGFloat ratio = 1;
      
      if (width > maxWidth || height > maxHeight)
      {
        CGFloat widthRatio = maxWidth/width;
        CGFloat heightRatio = maxHeight/height;
        
        ratio = MIN(widthRatio, heightRatio);
      }
      
      CGSize thumbSize = CGSizeMake(width*ratio, height*ratio);
      
      UIGraphicsBeginImageContext(thumbSize);
      [original drawInRect: CGRectMake(0, 0, thumbSize.width, thumbSize.height)];
      
      UIImage* thumb = UIGraphicsGetImageFromCurrentImageContext();
      NSData* thumbData = nil;
      NSString* mimeType = [self mimeTypeForFileAtPath: originalData];
      
      if (mimeType == nil)
      {
        dispatch_async(dispatch_get_main_queue(), ^{
          CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                            messageAsString: [NSString stringWithFormat: @"Unable to load file at %@", filePath]];
          
          [self.commandDelegate sendPluginResult: pluginResult callbackId: command.callbackId];
        });
      }
      else
      {
        if ([mimeType isEqualToString:@"image/png"])
        {
          thumbData = UIImagePNGRepresentation(thumb);
        }
        else
        {
          thumbData = UIImageJPEGRepresentation(thumb, quality / 100.0);
        }
        
        NSString* thumbBase64 = [thumbData base64EncodedString];
        NSString* message = [NSString stringWithFormat: @"data:%@;base64,%@", mimeType, thumbBase64];
        
        dispatch_async(dispatch_get_main_queue(), ^{
          CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString: message];
          
          [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        });
      }
      
      UIGraphicsEndImageContext();
    }
    @catch (NSException *exception)
    {
      dispatch_async(dispatch_get_main_queue(), ^{
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR
                                                          messageAsString: exception.reason];
        
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
      });
    }
  });
}

- (NSString*) mimeTypeForFileAtPath: (NSData*) fileData
{
  if (fileData.length == 0)
  {
    return nil;
  }
  else
  {
    uint8_t c;
    
    [fileData getBytes: &c length: 1];
    
    switch (c)
    {
      case 0xFF:
      {
        return @"image/jpeg";
      }
      case 0x89:
      {
        return @"image/png";
      }
      case 0x47:
      {
        return @"image/gif";
      }
      default:
      {
        return nil;
      }
    }
  }
}

@end