import SwiftUI

/// Shared presentation palette; connection and audio state remain in VoiceModel.
enum VoxkinBrand {
    static let navy = Color(red: 0.025, green: 0.045, blue: 0.09)
    static let surface = Color(red: 0.055, green: 0.075, blue: 0.14)
    static let cyan = Color(red: 0.04, green: 0.82, blue: 0.94)
    static let blue = Color(red: 0.12, green: 0.36, blue: 1)
    static let purple = Color(red: 0.49, green: 0.20, blue: 1)
    static let magenta = Color(red: 0.84, green: 0.26, blue: 0.96)
    static let gradient = LinearGradient(colors: [cyan, blue, purple], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let background = LinearGradient(colors: [surface, navy, navy], startPoint: .topLeading, endPoint: .bottomTrailing)
}

struct VoxkinWordmark: View {
    var body: some View {
        (Text("Vox").foregroundColor(.white) + Text("kin").foregroundColor(VoxkinBrand.magenta))
            .font(.system(.title2, design: .rounded, weight: .bold))
            .accessibilityLabel("Voxkin")
    }
}

/// Native vector rendering of brand/voxkin-mark.svg; no bitmap scaling in the UI.
struct VoxkinMark: View {
    private func color(_ hex: UInt32) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    var body: some View {
        Canvas { context, size in
            let scale = min(size.width / 800, size.height / 425)
            context.translateBy(x: (size.width - 800 * scale) / 2, y: (size.height - 425 * scale) / 2)
            context.scaleBy(x: scale, y: scale)
            context.translateBy(x: -100, y: -270)
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

            context.fill(Path(ribbon), with: .linearGradient(Gradient(colors: [color(0xEE67F7), color(0x7939FF), color(0x4932F5)]), startPoint: CGPoint(x: 700, y: 280), endPoint: CGPoint(x: 500, y: 680)))
            context.fill(Path(front), with: .linearGradient(Gradient(colors: [color(0x12D9EC), color(0x087EFF), color(0x04CBEA)]), startPoint: CGPoint(x: 330, y: 285), endPoint: CGPoint(x: 530, y: 685)))
            for (x, y, height, left) in [(110.0, 448.0, 104.0, true), (175.0, 410.0, 180.0, true), (783.0, 410.0, 180.0, false), (848.0, 448.0, 104.0, false)] {
                let bar = Path(roundedRect: CGRect(x: x, y: y, width: 42, height: height), cornerRadius: 21)
                context.fill(bar, with: .linearGradient(Gradient(colors: left ? [color(0x12D9EC), color(0x087EFF)] : [color(0xEE67F7), color(0x7939FF)]), startPoint: CGPoint(x: x, y: y), endPoint: CGPoint(x: x, y: y + height)))
            }
        }
    }
}
