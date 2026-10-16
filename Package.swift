// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "Voxkin", platforms: [.macOS(.v13), .iOS(.v17)], products: [.library(name: "VoiceCore", targets: ["VoiceCore"])], targets: [.target(name: "VoiceCore"), .testTarget(name: "VoiceCoreTests", dependencies: ["VoiceCore"])])
