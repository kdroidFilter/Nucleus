#import <Cocoa/Cocoa.h>
#import <objc/runtime.h>
#include <jni.h>
#include <math.h>

// Associated object keys
static const char kTitleBarConstraintsKey   = 0;
static const char kTitleBarHeightKey        = 1;
static const char kFullscreenObserverKey    = 2;
static const char kFullscreenButtonsKey     = 3;
static const char kOriginalButtonsParentKey = 4;
static const char kZoomResponderKey         = 5;
static const char kDragViewKey              = 6;

static const float kMinHeightForFullSize = 28.0f;
static const float kDefaultButtonOffset  = 20.0f;
static const float kFullscreenButtonsWidth = 80.0f;
static const float kFullscreenButtonsX     = 6.0f;

// _adjustWindowToScreen swizzle state
static BOOL sAdjustWindowSwizzled = NO;
static IMP sOriginalAdjustWindowToScreen = NULL;

// Forward declarations
static void applyConstraints(NSWindow *window, float height);
static void removeExistingConstraints(NSWindow *window);
static void installFullScreenButtons(NSWindow *window, float titleBarHeight);
static void removeFullScreenButtons(NSWindow *window);
static void updateFullScreenButtonsPosition(NSWindow *window);
static void ensureAdjustWindowSwizzle(NSWindow *window);
static void installZoomButtonResponder(NSWindow *window);
static void removeZoomButtonResponder(NSWindow *window);
static void ensureDragView(NSWindow *window);
static void removeDragView(NSWindow *window);

// ─── Fullscreen buttons container ───────────────────────────────────────────────

// Custom NSView that hosts replacement traffic-light buttons in the content view
// during fullscreen, mirroring JBR's AWTButtonsView.
@interface NucleusButtonsView : NSView
@end

@implementation NucleusButtonsView
@end

// ─── Fullscreen observer ────────────────────────────────────────────────────────

@interface NucleusFSObserver : NSObject
@property (nonatomic, weak) NSWindow *window;
@end

@implementation NucleusFSObserver

- (instancetype)initWithWindow:(NSWindow *)window {
    self = [super init];
    if (self) {
        _window = window;
        NSNotificationCenter *nc = [NSNotificationCenter defaultCenter];
        [nc addObserver:self selector:@selector(willEnterFullScreen:)
                   name:NSWindowWillEnterFullScreenNotification object:window];
        [nc addObserver:self selector:@selector(didEnterFullScreen:)
                   name:NSWindowDidEnterFullScreenNotification object:window];
        [nc addObserver:self selector:@selector(willExitFullScreen:)
                   name:NSWindowWillExitFullScreenNotification object:window];
        [nc addObserver:self selector:@selector(didExitFullScreen:)
                   name:NSWindowDidExitFullScreenNotification object:window];
    }
    return self;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

// About to enter fullscreen — remove constraints and drag view so macOS can animate cleanly
- (void)willEnterFullScreen:(NSNotification *)note {
    NSWindow *w = self.window;
    if (!w) return;

    removeDragView(w);
    removeExistingConstraints(w);
    [w setTitlebarAppearsTransparent:NO];
    [w setTitleVisibility:NSWindowTitleVisible];
    [w setMovable:YES];
}

// Finished entering fullscreen — install replacement buttons in the content view
- (void)didEnterFullScreen:(NSNotification *)note {
    NSWindow *w = self.window;
    if (!w) return;

    NSNumber *storedHeight = objc_getAssociatedObject(w, &kTitleBarHeightKey);
    float height = storedHeight ? [storedHeight floatValue] : kMinHeightForFullSize;

    installFullScreenButtons(w, height);
}

// About to exit fullscreen — remove replacement buttons
- (void)willExitFullScreen:(NSNotification *)note {
    NSWindow *w = self.window;
    if (!w) return;

    removeFullScreenButtons(w);
}

// Finished exiting fullscreen — restore the custom title bar
- (void)didExitFullScreen:(NSNotification *)note {
    NSWindow *w = self.window;
    if (!w) return;

    NSNumber *storedHeight = objc_getAssociatedObject(w, &kTitleBarHeightKey);
    if (!storedHeight) return;

    float height = [storedHeight floatValue];
    [w setTitlebarAppearsTransparent:YES];
    [w setTitleVisibility:NSWindowTitleHidden];
    [w setMovable:NO];
    applyConstraints(w, height);
}

@end

// ─── Zoom button responder ──────────────────────────────────────────────────────

// Temporarily re-enables window.movable when the mouse enters the zoom button,
// allowing macOS 15 window tiling to work even though movable is normally NO.
// Mirrors JBR's AWTWindowZoomButtonMouseResponder.
@interface NucleusZoomButtonResponder : NSObject
@property (nonatomic, weak) NSWindow *window;
@property (nonatomic, strong) NSTrackingArea *trackingArea;
@end

@implementation NucleusZoomButtonResponder

- (instancetype)initWithWindow:(NSWindow *)window {
    self = [super init];
    if (self) {
        _window = window;
        NSView *zoomButton = [window standardWindowButton:NSWindowZoomButton];
        if (zoomButton) {
            _trackingArea = [[NSTrackingArea alloc]
                initWithRect:zoomButton.bounds
                     options:(NSTrackingMouseEnteredAndExited | NSTrackingActiveInKeyWindow)
                       owner:self
                    userInfo:nil];
            [zoomButton addTrackingArea:_trackingArea];
        }
    }
    return self;
}

- (void)dealloc {
    if (_trackingArea) {
        NSView *zoomButton = _window ? [_window standardWindowButton:NSWindowZoomButton] : nil;
        if (zoomButton) {
            [zoomButton removeTrackingArea:_trackingArea];
        }
    }
}

- (void)mouseEntered:(NSEvent *)event {
    NSWindow *w = self.window;
    if (w && ![w isMovable]) {
        [w setMovable:YES];
    }
}

- (void)mouseExited:(NSEvent *)event {
    NSWindow *w = self.window;
    if (w && objc_getAssociatedObject(w, &kTitleBarHeightKey)) {
        [w setMovable:NO];
    }
}

@end

// ─── Native drag view ───────────────────────────────────────────────────────────

// Native NSView placed in the titlebar that handles window dragging via
// performWindowDragWithEvent: and double-click zoom/minimize.
// Mirrors JBR's AWTWindowDragView. All events are forwarded to the content
// view so AWT/Compose can process them normally.
// Pure pass-through view: forwards every event to the content view so
// AWT/Compose can process them. Window dragging is initiated by Compose
// via nativeStartWindowDrag when it detects an unconsumed drag, exactly
// mirroring JBR's forceHitTest approach where the decision lives in Compose.
@interface NucleusDragView : NSView
@property (atomic, strong) NSEvent *lastMouseDownEvent;
@end

@implementation NucleusDragView

- (BOOL)acceptsFirstMouse:(NSEvent *)event {
    return YES;
}

- (BOOL)shouldDelayWindowOrderingForEvent:(NSEvent *)event {
    return [[self.window contentView] shouldDelayWindowOrderingForEvent:event];
}

- (void)mouseDown:(NSEvent *)event {
    NSLog(@"[Nucleus] DragView mouseDown: loc=(%f,%f) movable=%d mainThread=%d",
          event.locationInWindow.x, event.locationInWindow.y,
          [self.window isMovable], [NSThread isMainThread]);
    self.lastMouseDownEvent = event;
    [[self.window contentView] mouseDown:event];
    NSLog(@"[Nucleus] DragView mouseDown: forwarded to contentView (%@)", [self.window contentView]);
}

- (void)mouseUp:(NSEvent *)event {
    NSLog(@"[Nucleus] DragView mouseUp");
    self.lastMouseDownEvent = nil;
    [[self.window contentView] mouseUp:event];
}

- (void)mouseDragged:(NSEvent *)event {
    NSLog(@"[Nucleus] DragView mouseDragged: loc=(%f,%f) mainThread=%d",
          event.locationInWindow.x, event.locationInWindow.y, [NSThread isMainThread]);
    [[self.window contentView] mouseDragged:event];
    NSLog(@"[Nucleus] DragView mouseDragged: forwarded done");
}

- (void)mouseMoved:(NSEvent *)event {
    [[self.window contentView] mouseMoved:event];
}

- (void)rightMouseDown:(NSEvent *)event {
    [[self.window contentView] rightMouseDown:event];
}

- (void)rightMouseUp:(NSEvent *)event {
    [[self.window contentView] rightMouseUp:event];
}

- (void)rightMouseDragged:(NSEvent *)event {
    [[self.window contentView] rightMouseDragged:event];
}

- (void)otherMouseDown:(NSEvent *)event {
    [[self.window contentView] otherMouseDown:event];
}

- (void)otherMouseUp:(NSEvent *)event {
    [[self.window contentView] otherMouseUp:event];
}

- (void)otherMouseDragged:(NSEvent *)event {
    [[self.window contentView] otherMouseDragged:event];
}

- (void)mouseEntered:(NSEvent *)event {
    [[self.window contentView] mouseEntered:event];
}

- (void)mouseExited:(NSEvent *)event {
    [[self.window contentView] mouseExited:event];
}

- (void)scrollWheel:(NSEvent *)event {
    [[self.window contentView] scrollWheel:event];
}

@end

// ─── Fullscreen button helpers ──────────────────────────────────────────────────

// Hides the native NSToolbarFullScreenWindow so the system hover toolbar
// doesn't overlap with our replacement buttons.
static void hideToolbarFullScreenWindow(void) {
    for (NSWindow *win in [[NSApplication sharedApplication] windows]) {
        if ([win isKindOfClass:NSClassFromString(@"NSToolbarFullScreenWindow")]) {
            [win.contentView setHidden:YES];
        }
    }
}

// Creates replacement traffic-light buttons in the content view,
// mirroring JBR's setWindowFullScreenControls.
static void installFullScreenButtons(NSWindow *window, float titleBarHeight) {
    // Don't double-install
    if (objc_getAssociatedObject(window, &kFullscreenButtonsKey)) return;

    // Capture the original buttons' parent (titlebar view) for frame reference
    NSView *origClose = [window standardWindowButton:NSWindowCloseButton];
    if (!origClose) return;
    objc_setAssociatedObject(window, &kOriginalButtonsParentKey,
                             origClose.superview, OBJC_ASSOCIATION_ASSIGN);

    NSRect closeRect = [[window standardWindowButton:NSWindowCloseButton] frame];
    NSRect miniRect  = [[window standardWindowButton:NSWindowMiniaturizeButton] frame];
    NSRect zoomRect  = [[window standardWindowButton:NSWindowZoomButton] frame];

    // Hide the native toolbar fullscreen window
    hideToolbarFullScreenWindow();

    // Create container
    NucleusButtonsView *container = [[NucleusButtonsView alloc] init];

    // Position the container (non-flipped: y=0 is at the bottom)
    NSView *parent = window.contentView;
    CGFloat h = origClose.superview.frame.size.height;
    CGFloat y = parent.frame.size.height - h - (titleBarHeight - h) / 2.0;
    [container setFrame:NSMakeRect(kFullscreenButtonsX, y,
                                   kFullscreenButtonsWidth - kFullscreenButtonsX, h)];

    NSUInteger masks = [window styleMask];

    // Create replacement buttons with original frames
    NSButton *closeButton = [NSWindow standardWindowButton:NSWindowCloseButton forStyleMask:masks];
    [closeButton setFrame:closeRect];
    [container addSubview:closeButton];

    NSButton *miniButton = [NSWindow standardWindowButton:NSWindowMiniaturizeButton forStyleMask:masks];
    [miniButton setFrame:miniRect];
    [container addSubview:miniButton];

    NSButton *zoomButton = [NSWindow standardWindowButton:NSWindowZoomButton forStyleMask:masks];
    [zoomButton setFrame:zoomRect];
    [container addSubview:zoomButton];

    // Wire up button actions to the actual window
    [closeButton setTarget:window];
    [closeButton setAction:@selector(performClose:)];
    [miniButton setTarget:window];
    [miniButton setAction:@selector(performMiniaturize:)];
    [zoomButton setTarget:window];
    [zoomButton setAction:@selector(toggleFullScreen:)];

    [parent addSubview:container];

    objc_setAssociatedObject(window, &kFullscreenButtonsKey, container,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

// Removes the replacement fullscreen buttons.
static void removeFullScreenButtons(NSWindow *window) {
    NucleusButtonsView *container = objc_getAssociatedObject(window, &kFullscreenButtonsKey);
    if (!container) return;

    [container removeFromSuperview];
    objc_setAssociatedObject(window, &kFullscreenButtonsKey, nil,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    objc_setAssociatedObject(window, &kOriginalButtonsParentKey, nil,
                             OBJC_ASSOCIATION_ASSIGN);
}

// Repositions the fullscreen button container (called from layout passes).
// Same formula as JBR's updateFullScreenButtons.
static void updateFullScreenButtonsPosition(NSWindow *window) {
    NucleusButtonsView *container = objc_getAssociatedObject(window, &kFullscreenButtonsKey);
    if (!container) return;

    NSView *origParent = objc_getAssociatedObject(window, &kOriginalButtonsParentKey);
    NSView *parent = window.contentView;
    if (!parent) return;

    NSNumber *storedHeight = objc_getAssociatedObject(window, &kTitleBarHeightKey);
    float titleBarHeight = storedHeight ? [storedHeight floatValue] : kMinHeightForFullSize;

    CGFloat h = origParent ? origParent.frame.size.height : container.frame.size.height;
    CGFloat y = parent.frame.size.height - h - (titleBarHeight - h) / 2.0;
    [container setFrame:NSMakeRect(kFullscreenButtonsX, y,
                                   kFullscreenButtonsWidth - kFullscreenButtonsX, h)];
}

// ─── _adjustWindowToScreen swizzle ──────────────────────────────────────────────

// macOS calls _adjustWindowToScreen for window snapping/tiling near screen edges.
// Since we set movable=NO, this callback is blocked. Override to temporarily
// re-enable movable (mirrors JBR's AWTWindow_Normal._adjustWindowToScreen).
static void nucleus_adjustWindowToScreen(id self, SEL _cmd) {
    NSNumber *storedHeight = objc_getAssociatedObject(self, &kTitleBarHeightKey);
    BOOL needsRestore = storedHeight && ![(NSWindow *)self isMovable];

    if (needsRestore) {
        [(NSWindow *)self setMovable:YES];
    }

    if (sOriginalAdjustWindowToScreen) {
        ((void (*)(id, SEL))sOriginalAdjustWindowToScreen)(self, _cmd);
    }

    updateFullScreenButtonsPosition((NSWindow *)self);

    if (needsRestore) {
        [(NSWindow *)self setMovable:NO];
    }
}

static void ensureAdjustWindowSwizzle(NSWindow *window) {
    if (sAdjustWindowSwizzled) return;
    sAdjustWindowSwizzled = YES;

    Class cls = object_getClass(window);
    SEL sel = NSSelectorFromString(@"_adjustWindowToScreen");
    Method method = class_getInstanceMethod(cls, sel);
    if (method) {
        sOriginalAdjustWindowToScreen = method_getImplementation(method);
        method_setImplementation(method, (IMP)nucleus_adjustWindowToScreen);
    }
}

// ─── Zoom button responder helpers ──────────────────────────────────────────────

static void installZoomButtonResponder(NSWindow *window) {
    if (objc_getAssociatedObject(window, &kZoomResponderKey)) return;

    NucleusZoomButtonResponder *responder =
        [[NucleusZoomButtonResponder alloc] initWithWindow:window];
    objc_setAssociatedObject(window, &kZoomResponderKey, responder,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static void removeZoomButtonResponder(NSWindow *window) {
    objc_setAssociatedObject(window, &kZoomResponderKey, nil,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

// ─── Drag view helpers ──────────────────────────────────────────────────────────

// Installs the drag view once in the titlebar. Subsequent calls are no-ops.
// The drag view persists across constraint updates so an in-progress drag
// is never interrupted by Compose layout passes.
static void ensureDragView(NSWindow *window) {
    if (objc_getAssociatedObject(window, &kDragViewKey)) {
        NSLog(@"[Nucleus] ensureDragView: already installed");
        return;
    }

    NSView *closeBtn = [window standardWindowButton:NSWindowCloseButton];
    if (!closeBtn) { NSLog(@"[Nucleus] ensureDragView: no closeBtn!"); return; }
    NSView *titlebar = closeBtn.superview;
    if (!titlebar) { NSLog(@"[Nucleus] ensureDragView: no titlebar!"); return; }

    NucleusDragView *dragView = [[NucleusDragView alloc] init];
    [titlebar addSubview:dragView positioned:NSWindowBelow relativeTo:closeBtn];
    objc_setAssociatedObject(window, &kDragViewKey, dragView, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
    NSLog(@"[Nucleus] ensureDragView: INSTALLED dragView=%@ in titlebar=%@ (class=%@)",
          dragView, titlebar, NSStringFromClass([titlebar class]));
}

static void removeDragView(NSWindow *window) {
    NucleusDragView *dragView = objc_getAssociatedObject(window, &kDragViewKey);
    if (!dragView) return;
    [dragView removeFromSuperview];
    objc_setAssociatedObject(window, &kDragViewKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

// ─── Constraint helpers ─────────────────────────────────────────────────────────

static void removeExistingConstraints(NSWindow *window) {
    NSMutableArray *existing = objc_getAssociatedObject(window, &kTitleBarConstraintsKey);
    if (!existing) return;

    [NSLayoutConstraint deactivateConstraints:existing];
    objc_setAssociatedObject(window, &kTitleBarConstraintsKey, nil, OBJC_ASSOCIATION_RETAIN_NONATOMIC);

    // Note: drag view is NOT removed here — it persists across constraint
    // updates so an in-progress drag is never interrupted.

    // Restore autoresizing mask so AppKit can manage layout again
    NSView *closeBtn = [window standardWindowButton:NSWindowCloseButton];
    if (!closeBtn) return;
    NSView *titlebar          = closeBtn.superview;
    NSView *titlebarContainer = titlebar ? titlebar.superview : nil;

    if (titlebarContainer) {
        titlebarContainer.translatesAutoresizingMaskIntoConstraints = YES;
    }
    if (titlebar) {
        titlebar.translatesAutoresizingMaskIntoConstraints = YES;
    }
    closeBtn.translatesAutoresizingMaskIntoConstraints = YES;
    NSView *miniBtn = [window standardWindowButton:NSWindowMiniaturizeButton];
    NSView *zoomBtn = [window standardWindowButton:NSWindowZoomButton];
    if (miniBtn) miniBtn.translatesAutoresizingMaskIntoConstraints = YES;
    if (zoomBtn) zoomBtn.translatesAutoresizingMaskIntoConstraints = YES;
}

static void applyConstraints(NSWindow *window, float height) {
    NSView *closeBtn = [window standardWindowButton:NSWindowCloseButton];
    NSView *miniBtn  = [window standardWindowButton:NSWindowMiniaturizeButton];
    NSView *zoomBtn  = [window standardWindowButton:NSWindowZoomButton];
    if (!closeBtn || !miniBtn || !zoomBtn) return;

    NSView *titlebar          = closeBtn.superview;
    NSView *titlebarContainer = titlebar ? titlebar.superview : nil;
    NSView *themeFrame        = titlebarContainer ? titlebarContainer.superview : nil;
    if (!themeFrame) return;

    removeExistingConstraints(window);

    NSMutableArray *constraints = [NSMutableArray array];

    titlebarContainer.translatesAutoresizingMaskIntoConstraints = NO;
    [constraints addObjectsFromArray:@[
        [titlebarContainer.leftAnchor  constraintEqualToAnchor:themeFrame.leftAnchor],
        [titlebarContainer.widthAnchor constraintEqualToAnchor:themeFrame.widthAnchor],
        [titlebarContainer.topAnchor   constraintEqualToAnchor:themeFrame.topAnchor],
        [titlebarContainer.heightAnchor constraintEqualToConstant:height],
    ]];

    titlebar.translatesAutoresizingMaskIntoConstraints = NO;
    [constraints addObjectsFromArray:@[
        [titlebar.leftAnchor   constraintEqualToAnchor:titlebarContainer.leftAnchor],
        [titlebar.rightAnchor  constraintEqualToAnchor:titlebarContainer.rightAnchor],
        [titlebar.topAnchor    constraintEqualToAnchor:titlebarContainer.topAnchor],
        [titlebar.bottomAnchor constraintEqualToAnchor:titlebarContainer.bottomAnchor],
    ]];

    // Add constraints for the drag view (installed once by ensureDragView)
    NucleusDragView *dragView = objc_getAssociatedObject(window, &kDragViewKey);
    if (dragView) {
        dragView.translatesAutoresizingMaskIntoConstraints = NO;
        [constraints addObjectsFromArray:@[
            [dragView.leftAnchor   constraintEqualToAnchor:titlebarContainer.leftAnchor],
            [dragView.rightAnchor  constraintEqualToAnchor:titlebarContainer.rightAnchor],
            [dragView.topAnchor    constraintEqualToAnchor:titlebarContainer.topAnchor],
            [dragView.bottomAnchor constraintEqualToAnchor:titlebarContainer.bottomAnchor],
        ]];
    }

    float shrinkFactor = fminf(height / kMinHeightForFullSize, 1.0f);
    float offset       = shrinkFactor * kDefaultButtonOffset;

    NSArray *buttons = @[closeBtn, miniBtn, zoomBtn];
    [buttons enumerateObjectsUsingBlock:^(NSView *btn, NSUInteger idx, BOOL *stop) {
        btn.translatesAutoresizingMaskIntoConstraints = NO;
        [constraints addObjectsFromArray:@[
            [btn.widthAnchor  constraintLessThanOrEqualToAnchor:titlebarContainer.heightAnchor
                                                     multiplier:0.5],
            [btn.heightAnchor constraintEqualToAnchor:btn.widthAnchor
                                           multiplier:14.0 / 12.0
                                             constant:-2.0],
            [btn.centerYAnchor constraintEqualToAnchor:titlebarContainer.centerYAnchor],
            [btn.centerXAnchor constraintEqualToAnchor:titlebarContainer.leftAnchor
                                              constant:(height / 2.0f + idx * offset)],
        ]];
    }];

    [NSLayoutConstraint activateConstraints:constraints];
    objc_setAssociatedObject(window, &kTitleBarConstraintsKey, constraints,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static void ensureFullscreenObserver(NSWindow *window) {
    NucleusFSObserver *existing = objc_getAssociatedObject(window, &kFullscreenObserverKey);
    if (existing) return;

    NucleusFSObserver *observer = [[NucleusFSObserver alloc] initWithWindow:window];
    objc_setAssociatedObject(window, &kFullscreenObserverKey, observer,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

static void removeFullscreenObserver(NSWindow *window) {
    objc_setAssociatedObject(window, &kFullscreenObserverKey, nil,
                             OBJC_ASSOCIATION_RETAIN_NONATOMIC);
}

// ─── NSWindow pointer extraction from AWT Window ────────────────────────────────

// Extracts the native NSWindow pointer from a java.awt.Window via JNI.
// Uses direct field access to Component.peer (bypasses module system entirely).
// JNI GetFieldID/GetObjectField don't check module boundaries or access modifiers,
// so this works in both standard JVM and GraalVM native-image.
static jlong getNSWindowPtrFromAWTWindow(JNIEnv *env, jobject awtWindow) {
    if (!awtWindow) return 0;

    // Direct field access: java.awt.Component.peer (package-private field)
    // JNI doesn't check access modifiers, so this works regardless of module system.
    jclass componentClass = (*env)->FindClass(env, "java/awt/Component");
    if (!componentClass || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    jfieldID peerField = (*env)->GetFieldID(env, componentClass,
        "peer", "Ljava/awt/peer/ComponentPeer;");
    if (!peerField || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    jobject peer = (*env)->GetObjectField(env, awtWindow, peerField);
    if (!peer) return 0;

    // peer.getPlatformWindow() — LWWindowPeer method
    jclass peerClass = (*env)->GetObjectClass(env, peer);
    jmethodID getPlatformWindow = (*env)->GetMethodID(env, peerClass,
        "getPlatformWindow", "()Lsun/lwawt/PlatformWindow;");
    if (!getPlatformWindow || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    jobject platformWindow = (*env)->CallObjectMethod(env, peer, getPlatformWindow);
    if (!platformWindow || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    // platformWindow.ptr (field in CFRetainedResource, parent of CPlatformWindow)
    jclass platformWindowClass = (*env)->GetObjectClass(env, platformWindow);
    jclass superClass = (*env)->GetSuperclass(env, platformWindowClass);
    if (!superClass) return 0;

    jfieldID ptrField = (*env)->GetFieldID(env, superClass, "ptr", "J");
    if (!ptrField || (*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        return 0;
    }

    return (*env)->GetLongField(env, platformWindow, ptrField);
}

// ─── JNI exports ────────────────────────────────────────────────────────────────

JNIEXPORT jlong JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativeGetNSWindowPtr(
    JNIEnv *env, jclass clazz, jobject awtWindow) {
    return getNSWindowPtrFromAWTWindow(env, awtWindow);
}

JNIEXPORT jfloat JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativeApplyTitleBar(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr, jfloat heightPt) {

    if (nsWindowPtr == 0) return 0.0f;

    float shrink    = fminf(heightPt / kMinHeightForFullSize, 1.0f);
    float btnOffset = shrink * kDefaultButtonOffset;
    float leftInset = heightPt + 2.0f * btnOffset;

    NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
    float capturedHeight = heightPt;

    NSLog(@"[Nucleus] nativeApplyTitleBar: called from thread=%@ mainThread=%d height=%f",
          [NSThread currentThread], [NSThread isMainThread], heightPt);

    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            NSLog(@"[Nucleus] nativeApplyTitleBar dispatch_async: EXECUTING on mainThread=%d", [NSThread isMainThread]);

            // Store the desired height for fullscreen restore
            objc_setAssociatedObject(window, &kTitleBarHeightKey,
                                     @(capturedHeight), OBJC_ASSOCIATION_RETAIN_NONATOMIC);

            ensureFullscreenObserver(window);
            ensureAdjustWindowSwizzle(window);
            installZoomButtonResponder(window);

            if ((window.styleMask & NSWindowStyleMaskFullScreen) != 0) {
                // In fullscreen: update replacement button positions
                updateFullScreenButtonsPosition(window);
                return;
            }

            [window setTitlebarAppearsTransparent:YES];
            [window setTitleVisibility:NSWindowTitleHidden];
            [window setMovable:NO];
            NSLog(@"[Nucleus] nativeApplyTitleBar: setMovable:NO done, movable=%d", [window isMovable]);

            ensureDragView(window);
            applyConstraints(window, capturedHeight);
            NSLog(@"[Nucleus] nativeApplyTitleBar: setup complete");
        }
    });

    return leftInset;
}

JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativeResetTitleBar(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {

    if (nsWindowPtr == 0) return;
    NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            removeFullScreenButtons(window);
            removeFullscreenObserver(window);
            removeZoomButtonResponder(window);
            removeDragView(window);
            removeExistingConstraints(window);
            objc_setAssociatedObject(window, &kTitleBarHeightKey, nil,
                                     OBJC_ASSOCIATION_RETAIN_NONATOMIC);
            [window setTitlebarAppearsTransparent:NO];
            [window setTitleVisibility:NSWindowTitleVisible];
            [window setMovable:YES];
        }
    });
}

// Called from Kotlin on each layout pass during fullscreen to keep
// the replacement buttons positioned correctly.
JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativeUpdateFullScreenButtons(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {

    if (nsWindowPtr == 0) return;
    NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            updateFullScreenButtonsPosition(window);
        }
    });
}

// Performs the macOS title bar double-click action (zoom or minimize)
// respecting the user's system preference (AppleActionOnDoubleClick).
// Called from Compose when an unconsumed double-click is detected.
JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativePerformTitleBarDoubleClickAction(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {

    if (nsWindowPtr == 0) return;
    NSLog(@"[Nucleus] nativePerformTitleBarDoubleClickAction: called mainThread=%d", [NSThread isMainThread]);
    NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
    dispatch_async(dispatch_get_main_queue(), ^{
        @autoreleasepool {
            NSLog(@"[Nucleus] nativePerformTitleBarDoubleClickAction dispatch_async: EXECUTING");
            NSString *action = [[NSUserDefaults standardUserDefaults]
                stringForKey:@"AppleActionOnDoubleClick"];
            if (action && [action caseInsensitiveCompare:@"Minimize"] == NSOrderedSame) {
                [window performMiniaturize:nil];
            } else if (!action || [action caseInsensitiveCompare:@"None"] != NSOrderedSame) {
                [window performZoom:nil];
            }
        }
    });
}

// Initiates a native window drag using the saved mouseDown event.
// Called from the EDT when Compose detects an unconsumed drag in the title bar.
// This mirrors JBR's forceHitTest(false) path where Compose decides the drag.
JNIEXPORT void JNICALL
Java_io_github_kdroidfilter_nucleus_window_utils_macos_JniMacTitleBarBridge_nativeStartWindowDrag(
    JNIEnv *env, jclass clazz, jlong nsWindowPtr) {

    if (nsWindowPtr == 0) return;
    NSWindow *window = (__bridge NSWindow *)(void *)nsWindowPtr;
    NucleusDragView *dragView = objc_getAssociatedObject(window, &kDragViewKey);
    if (!dragView) { NSLog(@"[Nucleus] nativeStartWindowDrag: no dragView!"); return; }

    NSEvent *event = dragView.lastMouseDownEvent;
    if (!event) { NSLog(@"[Nucleus] nativeStartWindowDrag: no lastMouseDownEvent!"); return; }
    dragView.lastMouseDownEvent = nil;

    NSLog(@"[Nucleus] nativeStartWindowDrag: dispatching performWindowDragWithEvent mainThread=%d", [NSThread isMainThread]);
    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@"[Nucleus] nativeStartWindowDrag dispatch_async: EXECUTING performWindowDragWithEvent");
        [window performWindowDragWithEvent:event];
    });
}
