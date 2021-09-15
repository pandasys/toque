
## Toque Architecture

The Toque application primarily consists of a service, MediaPlayerService, and a single activity,
MainActivity. MainActivity binds to the MediaPlayerService, but the service may also be running
without the activity for things such as playing audio in the background.

The MediaPlayerService contains various queues of playable media the user can switch between. The
initial implementation, when complete, will contain a local audio queue, a video queue, a casting
audio queue, and a queue of radio stations. The role of the MediaPlayerService is to manage the
various queues, create and contain a media session (which is the interface used by other
applications), provide a MediaBrowser interface,

As the current queue changes between these types, the
MainActivity reacts to the change and presents the user with the correct UI for that particular
queue.


## Audio File Tag Processing
There are 2 libraries used in Toque which can read audio file tag information: eAlvaTag and libVLC.
eAlvaTag can read and write tag information, while libVLC for Android currently only supports read.
eAlvaTag requires a File object to read/write tag information, while libVLC requires a Uri or a
FileDescriptor. Due to how permissions work with the latest versions of Android, Toque uses 2
strategies for playing and editing media files.

Toque obtains media from the Android MediaStore. Currently, the path to the media is available in
one of the MediaStore columns, but it is marked deprecated. The MediaStore also provides a content
URI for a piece of media. Given the content URI, a FileDescriptor can be obtained.

Currently, the Toque media scanner uses the file path and eAlvaTag to parse information from the
audio file tag. To do edits, the file content is copied to a Toque private area, the File which
points to this area is used to read/write the tag, and the result is then copied back to the
original location pointed to by the content URI.

For playback, the content URI is converted to a FileDescriptor so tha a libVLC Media object can
be constructed and passed to a libVLC MediaPlayer. These libVLC classes are wrapped by Toque classes
to hide libVLC specifics.
