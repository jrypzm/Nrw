# SF Pro font files

The app uses SF Pro first. iPhone, iPad and Mac already have it built in, so nothing is needed there.
On Android/Windows, put these 8 files in this folder (same folder level as `fonts/` next to `index.html`):

    SF-Pro-Display-Regular.otf   SF-Pro-Text-Regular.otf
    SF-Pro-Display-Medium.otf    SF-Pro-Text-Medium.otf
    SF-Pro-Display-Semibold.otf  SF-Pro-Text-Semibold.otf
    SF-Pro-Display-Bold.otf      SF-Pro-Text-Bold.otf

Get them from https://developer.apple.com/fonts/ ("SF Pro" download). The download is a .dmg/.pkg;
on a computer, extract it (on Windows, 7-Zip can open the .dmg, then the .pkg, then "Payload~")
and copy the files above from Library/Fonts.

Font files are git-ignored: Apple's licence does not allow redistributing them.
If they're missing, the app falls back to Inter automatically.
