import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(ApplicationDelegate.self)
    var appDelegate: ApplicationDelegate
    
    @Environment(\.scenePhase)
    var scenePhase: ScenePhase
    
    var rootHolder: RootHolder { appDelegate.rootHolder }
    
    var body: some Scene {
        return WindowGroup { ContentView(delegate: appDelegate) }
            .onChange(of: scenePhase) { newPhase in
                switch newPhase {
                    case .background: LifecycleRegistryExtKt.stop(rootHolder.appLifecycle)
                    case .inactive: LifecycleRegistryExtKt.pause(rootHolder.appLifecycle)
                    case .active: LifecycleRegistryExtKt.resume(rootHolder.appLifecycle)
                    @unknown default: break
                }
            }
    }
}
