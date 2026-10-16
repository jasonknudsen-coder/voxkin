// Voxkin vector icon. Geometry matches brand/voxkin-mark.svg (1000-unit canvas).
// Run: swift scripts/generate-icon.swift App/Assets.xcassets/AppIcon.appiconset/Icon.png
import AppKit
import ImageIO
import UniformTypeIdentifiers

let size = 1024
let context = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
    bytesPerRow: size * 4, space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
context.scaleBy(x: 1.024, y: 1.024)
// Use top-left coordinates, shared with SVG and SwiftUI.
context.translateBy(x: 0, y: 1000)
context.scaleBy(x: 1, y: -1)
func color(_ hex: UInt32) -> CGColor {
    CGColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
            blue: CGFloat(hex & 255) / 255, alpha: 1)
}
func gradient(_ path: CGPath, _ stops: [UInt32], from: CGPoint, to: CGPoint) {
    context.saveGState(); context.addPath(path); context.clip()
    let colors = stops.map { color($0) } as CFArray
    context.drawLinearGradient(CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: nil)!,
        start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    context.restoreGState()
}
let background = CGPath(rect: CGRect(x: 0, y: 0, width: 1000, height: 1000), transform: nil)
gradient(background, [0x0B1830, 0x040914], from: CGPoint(x: 100, y: 0), to: CGPoint(x: 800, y: 1000))
// Center the visible mark vertically within the square icon.
context.translateBy(x: 0, y: 20)
let ribbon = CGMutablePath()
ribbon.move(to: CGPoint(x: 330, y: 285))
ribbon.addCurve(to: CGPoint(x: 285, y: 361), control1: CGPoint(x: 290, y: 285), control2: CGPoint(x: 264, y: 324))
ribbon.addLine(to: CGPoint(x: 435, y: 634))
ribbon.addCurve(to: CGPoint(x: 588, y: 633), control1: CGPoint(x: 471, y: 699), control2: CGPoint(x: 549, y: 699))
ribbon.addLine(to: CGPoint(x: 737, y: 361))
ribbon.addCurve(to: CGPoint(x: 696, y: 281), control1: CGPoint(x: 760, y: 319), control2: CGPoint(x: 735, y: 281))
ribbon.addCurve(to: CGPoint(x: 645, y: 313), control1: CGPoint(x: 674, y: 281), control2: CGPoint(x: 657, y: 291))
ribbon.addLine(to: CGPoint(x: 513, y: 547))
ribbon.addLine(to: CGPoint(x: 382, y: 313))
ribbon.addCurve(to: CGPoint(x: 330, y: 285), control1: CGPoint(x: 370, y: 294), control2: CGPoint(x: 352, y: 285))
ribbon.closeSubpath()
gradient(ribbon, [0xEE67F7, 0x7939FF, 0x4932F5], from: CGPoint(x: 700, y: 280), to: CGPoint(x: 500, y: 680))
let front = CGMutablePath()
front.move(to: CGPoint(x: 330, y: 285))
front.addCurve(to: CGPoint(x: 285, y: 361), control1: CGPoint(x: 290, y: 285), control2: CGPoint(x: 264, y: 324))
front.addLine(to: CGPoint(x: 435, y: 634))
front.addCurve(to: CGPoint(x: 588, y: 633), control1: CGPoint(x: 471, y: 699), control2: CGPoint(x: 549, y: 699))
front.addLine(to: CGPoint(x: 613, y: 588))
front.addCurve(to: CGPoint(x: 513, y: 547), control1: CGPoint(x: 576, y: 633), control2: CGPoint(x: 541, y: 597))
front.addLine(to: CGPoint(x: 382, y: 313))
front.addCurve(to: CGPoint(x: 330, y: 285), control1: CGPoint(x: 370, y: 294), control2: CGPoint(x: 352, y: 285))
front.closeSubpath()
gradient(front, [0x12D9EC, 0x087EFF, 0x04CBEA], from: CGPoint(x: 330, y: 285), to: CGPoint(x: 530, y: 685))
for (x, y, height, left) in [(110.0, 448.0, 104.0, true), (175.0, 410.0, 180.0, true),
                            (783.0, 410.0, 180.0, false), (848.0, 448.0, 104.0, false)] {
    let path = CGPath(roundedRect: CGRect(x: x, y: y, width: 42, height: height), cornerWidth: 21, cornerHeight: 21, transform: nil)
    gradient(path, left ? [0x12D9EC, 0x087EFF] : [0xEE67F7, 0x7939FF],
             from: CGPoint(x: x, y: y), to: CGPoint(x: x, y: y + height))
}
let output = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "App/Assets.xcassets/AppIcon.appiconset/Icon.png"
let destination = CGImageDestinationCreateWithURL(URL(fileURLWithPath: output) as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, context.makeImage()!, nil)
precondition(CGImageDestinationFinalize(destination))
print("Generated opaque 1024 × 1024 Voxkin icon: \(output)")
