#import "VoxkinAudioInput.h"
#import <math.h>

@interface VoxkinAudioInput ()
@property(nonatomic, strong, nullable) AVAudioEngine *engine;
@property(nonatomic, strong, nullable) AVAudioNode *tappedNode;
@property(nonatomic) BOOL attempted;
@end

@implementation VoxkinAudioInput
- (instancetype)init { return [self initWithEngine:[[AVAudioEngine alloc] init]]; }
- (instancetype)initWithEngine:(AVAudioEngine *)engine {
    if ((self = [super init])) { _engine = engine; }
    return self;
}
- (BOOL)fail:(NSError * _Nullable * _Nullable)error code:(NSInteger)code {
    if (error) *error = [NSError errorWithDomain:@"VoxkinAudioInput" code:code
        userInfo:@{NSLocalizedDescriptionKey: @"Microphone route unavailable or changed. Tap to try again."}];
    return NO;
}
- (BOOL)startWithRequest:(SFSpeechAudioBufferRecognitionRequest *)request error:(NSError **)error {
    // Never attempt a second tap or reuse a graph after an exception.
    if (self.attempted || !self.engine) return [self fail:error code:1];
    self.attempted = YES;
    @try {
        AVAudioInputNode *input = self.engine.inputNode;
        if (input.numberOfInputs == 0 || input.numberOfOutputs == 0) {
            [self stop]; return [self fail:error code:2];
        }
        AVAudioFormat *hardware = [input inputFormatForBus:0];
        AVAudioFormat *output = [input outputFormatForBus:0];
        if (!isfinite(hardware.sampleRate) || !isfinite(output.sampleRate) ||
            hardware.sampleRate <= 0 || output.sampleRate <= 0 ||
            hardware.channelCount == 0 || output.channelCount == 0 ||
            hardware.sampleRate != output.sampleRate || hardware.channelCount != output.channelCount) {
            [self stop]; return [self fail:error code:2];
        }
        // Remember the exact node even if native installation partially succeeds.
        self.tappedNode = input;
        AVAudioFrameCount frames = (AVAudioFrameCount)MAX(1024, MIN(65536, ceil(output.sampleRate * 0.1)));
        AVAudioNodeTapBlock block = ^(AVAudioPCMBuffer *buffer, AVAudioTime *when) {
            [request appendAudioPCMBuffer:buffer];
        };
        // nil leaves the negotiated native format alone instead of applying a stale snapshot.
        if (@available(iOS 27.0, *)) {
            NSError *tapError = nil;
            if (![input installTapOnBus:0 bufferSize:frames format:nil error:&tapError block:block]) {
                [self stop]; return [self fail:error code:3];
            }
        } else {
            [input installTapOnBus:0 bufferSize:frames format:nil block:block];
        }
        [self.engine prepare];
        NSError *startError = nil;
        if (![self.engine startAndReturnError:&startError]) {
            [self stop]; return [self fail:error code:4];
        }
        return YES;
    } @catch (NSException *exception) {
        // Do not propagate exception payloads, continue this graph, or retry automatically.
        [self stop]; return [self fail:error code:5];
    }
}
- (void)stop {
    AVAudioEngine *engine = self.engine;
    AVAudioNode *node = self.tappedNode;
    self.engine = nil; self.tappedNode = nil;
    @try { [engine stop]; } @catch (NSException *exception) { }
    @try { if (node) [node removeTapOnBus:0]; } @catch (NSException *exception) { }
}
@end
