# NYT Games for Supernote

An unofficial little Android app that runs the New York Times games on a Supernote e-ink tablet (Manta, Nomad), tuned for a greyscale screen.

It opens the NYT games pages and their hint pages, and nothing else: no news, no other sites, no ads or trackers. You sign in with your own NYT account.

Not made by or affiliated with The New York Times. You need your own NYT Games subscription for the games that require one.

<p align="center">
  <img src="docs/crossword.png" width="45%" alt="The crossword on a Supernote Manta: a large grid filling the width, the current Across and Down clues in big type below it, and the keyboard at the bottom.">
  &nbsp;
  <img src="docs/wordle.png" width="45%" alt="Wordle on a Supernote Manta: right-spot tiles are black with white letters, wrong-spot tiles grey, and letters not in the word faded on white. The keyboard keys match.">
</p>
<p align="center"><sub>Screenshots from a Supernote Manta. Crossword: big grid and the two current clues. Wordle (example guesses): black = right spot, grey = wrong spot, faded = not in the word.</sub></p>

## What it changes

- **Crossword and Mini:** the full clue lists are hidden so the grid can be big. The current Across and Down clues sit under the grid in large type; tap one to switch. Highlighted squares use greys the e-ink panel shows clearly.
- **Wordle:** green and grey look almost the same on e-ink, so tiles are **black** for the right spot, **grey** for the wrong spot and **white with a faded letter** for "not in the word". The keyboard matches, and the board and keys are bigger.
- **Spelling Bee:** the centre letter is black, so it stands out.
- **Strands:** the letters you're tapping are black, the spangram dark grey, theme words mid grey.
- **Connections:** bigger on the Manta.
- Text is never shrunk below 100% (Supernote's system font setting otherwise shrinks every web page).
- **Hints:** each game's hint button (the lightbulb) opens its hint page, with a big **← Back to the game** bar at the bottom.
- If Wi-Fi is off, you get a "No connection" page with **Try again** and **Close**. It reloads by itself when Wi-Fi comes back.

## Install (about 5 minutes, no coding)

1. **Download** `NYT-Games.apk` from the [Releases page](../../releases/latest).
2. **On the Supernote:** Settings → Security & Privacy → turn on **Sideloading**.
3. **On your computer** (Mac, Windows or Linux): install [Android Sideloader](https://github.com/ryan-andrew/android_sideloader/releases). Plug the Supernote in with its USB cable, open Android Sideloader, click **Choose APK file**, pick `NYT-Games.apk`, then **Install APK**.
4. **On the Supernote:** open **NYT Games** from your apps and sign in to your NYT account (menu ☰ → Log in). You stay signed in after that.

   **Normally sign in with Google, Apple or Facebook?** Those buttons won't work in this app. Type your account's email address instead and choose to get a **one-time code by email**, then enter the code from your inbox. It signs you in to the same account.

To update, install the newer `NYT-Games.apk` the same way. Your login and puzzles are kept. (Version 1.1 was called Crossword; 1.2 installs over it.)

## Tips

- NYT sometimes shows a **Play** screen first; tap Play to continue where you left off.
- Use the ☰ menu to switch between games.
- The Back button goes back a page, and closes the app on the first page.

## Known limits

- Supernote's built-in web engine is an old Chrome (96) that can't be updated. The app patches around what NYT's site needs from newer Chrome, but an NYT redesign could break a game until the app is updated.
- Built and tested on a **Manta**. The **Nomad** layout was tested by simulating its screen size on a Manta. Reports from Nomad owners are very welcome; please open an issue.
- Other NYT games (Tiles, Sudoku, Letter Boxed and so on) open, but haven't been tuned for e-ink yet.

## How it works

- `src/app/helpyy/nyt/MainActivity.java`: a locked-down WebView. It only allows NYT game pages and the NYT login, blocks everything else, and shows the offline page.
- `res/raw/eink.css`: all the greyscale and sizing tweaks, added to NYT's own stylesheets as they load. Start here to adjust a colour or size.
- `res/raw/clues.js`: the big two-clue panel under the crossword grid.
- `res/raw/back.js`: the "Back to the game" bar on hint pages.

## Build it yourself

Needs Android Studio (for its SDK and Java) on a Mac; set `ANDROID_HOME` and `JAVA_BIN` elsewhere.

```bash
./build.sh
```

The first build makes a signing key in `~/.android/supernote-nyt.keystore`. An APK signed with your key won't install over one signed by someone else's, so uninstall the other copy first.

## License

MIT. See [LICENSE](LICENSE).
