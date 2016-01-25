#import "Thumbnail.h"
#import "NSData+Base64.h"

@implementation Thumbnail

- (void)makeThumbnail:(CDVInvokedUrlCommand*)command
{
    @try
    {
        NSString* filePath = [command.arguments objectAtIndex: 0];
    
        if ([filePath length] == 0)
        {
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString: @"no file path"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            
            return;
        }
        
        NSInteger maxWidth = [[command.arguments objectAtIndex: 1] integerValue];
        NSInteger maxHeight = [[command.arguments objectAtIndex: 2] integerValue];
        NSInteger quality = [[command.arguments objectAtIndex: 3] integerValue];
        NSData* originalData;
        
        if (![filePath containsString:@"http://"] && ![[filePath lowercaseString] hasPrefix: @"file://"])
        {
            if ([filePath characterAtIndex: 0] == '/')
                filePath = [NSString stringWithFormat:@"file://%@", filePath];
            else
                filePath = [NSString stringWithFormat:@"file:///%@", filePath];
        }
        
        NSURL* fileURL = [NSURL URLWithString: filePath];
        originalData = [NSData dataWithContentsOfURL: fileURL];
        
        if (!originalData)
        {
            NSString* message = [NSString stringWithFormat: @"Cannot find image file %@", filePath];
            CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString: message];
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
        NSData* thumbData = UIImageJPEGRepresentation(thumb, quality / 100.0);
        NSString* prefix = @"data:image/jpeg;base64,";
        NSString* thumbBase64 = [thumbData base64EncodedString];
        NSString* message = [prefix stringByAppendingString: thumbBase64];
        
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_OK messageAsString: message];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    @catch (NSException *exception)
    {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus: CDVCommandStatus_ERROR messageAsString: exception.reason];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }}

@end
