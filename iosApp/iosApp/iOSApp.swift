import SwiftUI
import UIKit
import ComposeApp

/// Hosts the shared Compose Multiplatform UI. Everything the user sees is Kotlin —
/// this file exists only to hand Compose a UIViewController and to forward deep links.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            // Compose draws its own insets, so let it own the whole window.
            .ignoresSafeArea(.all)
            .ignoresSafeArea(.keyboard)
    }
}

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    // splits://join/<code> — handed straight to the shared router.
                    DeepLinks.shared.offer(rawLink: url.absoluteString)
                }
        }
    }
}
