# Settings offers "Check for updates"

- Settings → About has a "Check for updates" row that opens depot's "App updates" screen, which checks at once and offers to install
- The row appears only in builds that carry depot's client: `app/src/depot/kotlin` calls `Depot.updatesIntent`, and builds without depot use `app/src/nodepot/kotlin`, whose `Updates` returns no intent
- android-emu is at e16d3c4, whose emu-publish requires the call
- `AppFlowTest.checkForUpdatesOpensTheUpdatersScreen` checks the row opens depot's screen; `LibraryBehaviourTest` checks it's hidden without an updater
