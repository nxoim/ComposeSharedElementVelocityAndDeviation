import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    var delegate: ApplicationDelegate
    
    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController(

        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var delegate: ApplicationDelegate
    
    var body: some View {
        ComposeView(delegate: delegate)
            .ignoresSafeArea()
    }
}

class ApplicationDelegate: NSObject, UIApplicationDelegate {
    let rootHolder = RootHolder()

    func application(_ application: UIApplication, shouldSaveSecureApplicationState coder: NSCoder) -> Bool {
        rootHolder.saveStateInStateKeeper(coder: coder)
        return true
    }
    
    func application(_ application: UIApplication, shouldRestoreSecureApplicationState coder: NSCoder) -> Bool {
        rootHolder.restoreStateFromStateKeeper(coder: coder)
        return true
    }
}

class RootHolder: ObservableObject {
    let appLifecycle: LifecycleRegistry
    private var appStateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: nil)
    let defaultComponentContext: DefaultComponentContext

    init() {
        appLifecycle = LifecycleRegistryKt.LifecycleRegistry()

                defaultComponentContext = DefaultComponentContext(
                    lifecycle: lifecycleRegistry,
                    stateKeeper: appStateKeeper,
                    instanceKeeper: nil,
                    backHandler: nil
                )

        LifecycleRegistryExtKt.create(appLifecycle)
    }
    
    deinit {
        LifecycleRegistryExtKt.destroy(appLifecycle)
    }
    
    func saveStateInStateKeeper(coder: NSCoder) {
        StateKeeperUtilsKt.save(coder: coder, state: appStateKeeper.save())
    }
    
    func restoreStateFromStateKeeper(coder: NSCoder) {
        appStateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(
            savedState: StateKeeperUtilsKt.restore(coder: coder)
        )
    }
}



