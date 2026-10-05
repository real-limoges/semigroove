#!/bin/sh
# Double-click in Finder to start the toddler Launchpad. Close the window to quit.
cd "$(dirname "$0")/.." || exit 1
osascript -e 'set volume output volume 35'   # cap the Mac's output; tune to the speakers
exec caffeinate -i clj -M:toddler            # keep the Mac from idling to sleep
