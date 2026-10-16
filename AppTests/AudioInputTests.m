#import <XCTest/XCTest.h>
#import "../App/VoxkinAudioInput.h"

// Selector-only format fixture permits invalid native snapshots without asking
// AVAudioFormat to construct an invalid object (which itself can assert).
@interface StubFormat : NSObject
@property double sampleRate;
@property AVAudioChannelCount channelCount;
@end
@implementation StubFormat
@end

@interface StubInput : AVAudioMixerNode
@property(nonatomic, strong) StubFormat *hardware;
@property(nonatomic, strong) StubFormat *output;
@property NSInteger installs;
@property NSInteger removals;
@property BOOL throwInstall;
@property BOOL rejectInstall;
@property BOOL throwRemove;
@property(nonatomic, strong) AVAudioFormat *receivedFormat;
@end
@implementation StubInput
- (instancetype)init {
    if ((self = [super init])) {
        _hardware = [StubFormat new]; _hardware.sampleRate = 48000; _hardware.channelCount = 1;
        _output = [StubFormat new]; _output.sampleRate = 48000; _output.channelCount = 1;
    }
    return self;
}
- (NSUInteger)numberOfInputs { return 1; }
- (NSUInteger)numberOfOutputs { return 1; }
- (AVAudioFormat *)inputFormatForBus:(AVAudioNodeBus)bus { return (AVAudioFormat *)self.hardware; }
- (AVAudioFormat *)outputFormatForBus:(AVAudioNodeBus)bus { return (AVAudioFormat *)self.output; }
- (void)installTapOnBus:(AVAudioNodeBus)bus bufferSize:(AVAudioFrameCount)size format:(AVAudioFormat *)format block:(AVAudioNodeTapBlock)block {
    self.installs++; self.receivedFormat = format;
    if (self.throwInstall) [NSException raise:NSInternalInconsistencyException format:@"Synthetic native install failure"];
}
- (BOOL)installTapOnBus:(AVAudioNodeBus)bus bufferSize:(AVAudioFrameCount)size format:(AVAudioFormat *)format error:(NSError **)error block:(AVAudioNodeTapBlock)block {
    [self installTapOnBus:bus bufferSize:size format:format block:block];
    if (self.rejectInstall) {
        if (error) *error = [NSError errorWithDomain:@"Fixture" code:1 userInfo:nil];
        return NO;
    }
    return YES;
}
- (void)removeTapOnBus:(AVAudioNodeBus)bus {
    self.removals++;
    if (self.throwRemove) [NSException raise:NSInternalInconsistencyException format:@"Synthetic cleanup failure"];
}
@end

@interface StubEngine : AVAudioEngine
@property(nonatomic, strong) StubInput *stubInput;
@property BOOL rejectStart;
@property BOOL throwPrepare;
@property BOOL throwStop;
@property NSInteger starts;
@property NSInteger stops;
@end
@implementation StubEngine
- (instancetype)init { if ((self = [super init])) _stubInput = [StubInput new]; return self; }
- (AVAudioInputNode *)inputNode { return (AVAudioInputNode *)self.stubInput; }
- (void)prepare { if (self.throwPrepare) [NSException raise:NSInternalInconsistencyException format:@"Synthetic prepare failure"]; }
- (BOOL)startAndReturnError:(NSError **)error {
    self.starts++;
    if (self.rejectStart) { if (error) *error = [NSError errorWithDomain:@"Fixture" code:2 userInfo:nil]; return NO; }
    return YES;
}
- (void)stop { self.stops++; if (self.throwStop) [NSException raise:NSInternalInconsistencyException format:@"Synthetic stop failure"]; }
@end

@interface AudioInputTests : XCTestCase
@end
@implementation AudioInputTests
- (void)testNativeExceptionIsContainedAndGraphDiscarded {
    StubEngine *engine = [StubEngine new]; engine.stubInput.throwInstall = YES;
    VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
    NSError *error = nil;
    XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:&error]);
    XCTAssertEqual(error.code, 5);
    XCTAssertFalse([error.localizedDescription containsString:@"Synthetic"]);
    XCTAssertEqual(engine.stubInput.removals, 1); XCTAssertEqual(engine.stops, 1);
    XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:&error]);
    XCTAssertEqual(engine.stubInput.installs, 1);
}
- (void)testThrowingStartupReleasesRequestAndGraph {
    __weak SFSpeechAudioBufferRecognitionRequest *weakRequest;
    __weak StubEngine *weakEngine;
    __weak StubInput *weakNode;
    @autoreleasepool {
        StubEngine *engine = [StubEngine new]; engine.stubInput.throwInstall = YES;
        SFSpeechAudioBufferRecognitionRequest *request = [SFSpeechAudioBufferRecognitionRequest new];
        weakRequest = request; weakEngine = engine; weakNode = engine.stubInput;
        VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
        XCTAssertFalse([input startWithRequest:request error:nil]);
    }
    XCTAssertNil(weakRequest); XCTAssertNil(weakEngine); XCTAssertNil(weakNode);
}
- (void)testSuccessUsesNativeFormatAndCannotInstallTwice {
    StubEngine *engine = [StubEngine new]; VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
    XCTAssertTrue([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    XCTAssertNil(engine.stubInput.receivedFormat); XCTAssertEqual(engine.starts, 1);
    XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    XCTAssertEqual(engine.stubInput.installs, 1);
    [input stop]; [input stop]; XCTAssertEqual(engine.stubInput.removals, 1);
}
- (void)testInvalidAndChangedFormatsNeverInstallTap {
    for (NSNumber *rate in @[@0, @(NAN), @44100]) {
        StubEngine *engine = [StubEngine new]; engine.stubInput.output.sampleRate = rate.doubleValue;
        VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
        XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
        XCTAssertEqual(engine.stubInput.installs, 0); XCTAssertEqual(engine.stops, 1);
    }
    StubEngine *engine = [StubEngine new]; engine.stubInput.output.channelCount = 0;
    XCTAssertFalse([[[VoxkinAudioInput alloc] initWithEngine:engine] startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    XCTAssertEqual(engine.stubInput.installs, 0);
}
- (void)testPrepareExceptionAndStartErrorRemoveTap {
    for (NSNumber *prepare in @[@YES, @NO]) {
        StubEngine *engine = [StubEngine new]; engine.throwPrepare = prepare.boolValue; engine.rejectStart = !prepare.boolValue;
        VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
        XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
        XCTAssertEqual(engine.stubInput.removals, 1); XCTAssertEqual(engine.stops, 1);
    }
}
- (void)testCleanupExceptionsDoNotEscapeOrRepeat {
    StubEngine *engine = [StubEngine new]; engine.throwStop = YES; engine.stubInput.throwRemove = YES;
    VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
    XCTAssertTrue([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    XCTAssertNoThrow([input stop]); XCTAssertNoThrow([input stop]);
    XCTAssertEqual(engine.stubInput.removals, 1); XCTAssertEqual(engine.stops, 1);
}
- (void)testFreshGraphCanSucceedAfterPriorFailure {
    StubEngine *bad = [StubEngine new]; bad.stubInput.throwInstall = YES;
    XCTAssertFalse([[[VoxkinAudioInput alloc] initWithEngine:bad] startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    StubEngine *good = [StubEngine new]; VoxkinAudioInput *retry = [[VoxkinAudioInput alloc] initWithEngine:good];
    XCTAssertTrue([retry startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:nil]);
    [retry stop]; XCTAssertEqual(good.stubInput.removals, 1);
}
- (void)testModernTapErrorIsRecoverable {
    if (@available(iOS 27.0, *)) {
        StubEngine *engine = [StubEngine new]; engine.stubInput.rejectInstall = YES;
        VoxkinAudioInput *input = [[VoxkinAudioInput alloc] initWithEngine:engine];
        NSError *error = nil;
        XCTAssertFalse([input startWithRequest:[SFSpeechAudioBufferRecognitionRequest new] error:&error]);
        XCTAssertEqual(error.code, 3); XCTAssertEqual(engine.stubInput.removals, 1);
    }
}
@end
