#import <AVFoundation/AVFoundation.h>
#import <Speech/Speech.h>

NS_ASSUME_NONNULL_BEGIN
/// One-shot engine owner. All exception-prone graph operations stay in Objective-C.
@interface VoxkinAudioInput : NSObject
- (instancetype)init;
- (instancetype)initWithEngine:(AVAudioEngine *)engine NS_DESIGNATED_INITIALIZER;
- (BOOL)startWithRequest:(SFSpeechAudioBufferRecognitionRequest *)request
                  error:(NSError * _Nullable * _Nullable)error NS_SWIFT_NAME(start(request:));
- (void)stop;
@end
NS_ASSUME_NONNULL_END
