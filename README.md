# Toque Player

An audio (video later) media player built using Jetpack Compose for the UI, Simple Stack for
navigation, Koin for top level dependency injection, Glance widgets, Retrofit clients to various
services to obtain artist/album information and artwork (MusicBrainz, LastFM, Spotify, Wikipedia),
Smart Playlist support, media file Tag editing, and MediaBrowserService/MediaSession support.

## Navigating Source Code

### User Interface

#### Activity/Screens
To begin investigating the UI design, start with MainActivity and MainScreen, which includes the
setup for navigation and the main Scaffold. The UI is primarily a single Activity containing
a Scaffold with a MainBottomSheet for navigation to primary components. Navigation sets the
content of the Scaffold, which are Screens defined as SimpleStack keys and the associated models.

Look at all implementations of the ComposeKey base class to explore the screens available. A
typical screen has a related view model which is registered via "bindServices" and the UI entry
point being "ScreenComposable". A global search for either "bindServices" or "ScreenComposable" will
show most screens and view models.

#### View Models
Mostly, there is a 1 to 1 correspondence between view model and screen. However, there is a
MainViewModel that handles binding to the MediaPlayerService, provides a PlayableQueue
(LocalAudio, Radio, Video, ...) flow, handles snackbars (Notification flow), and dialogs
(DialogPrompt flow), and some permission acquisition.

There is also a LocalAudioViewModel which connects to the underlying media player service.
Later there will als be LocalVideo, Radio, etc view models which communicate with the corresponding
playable queue within the media player service.

Screen view models obtain data via DAO objects which read the local SQLite DB, Retrofit interface
implementations to external services, and the MediaPlayerService/PlayableQueue. The screens are
provided data via one or more StateFlows of the view model. All view models have an interface
and typically a factory function (operator invoke in the companion) to obtain the instance.
