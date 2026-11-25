## Fix: Splits on iPhone opened to a blank screen

The iPhone web app from 1.8.0 showed "Splits" for a moment and then went blank, both in Safari
and from the Home Screen. The app was running underneath, but Safari never put it on screen.
It now opens normally. If you added it to your Home Screen already, just open it again: close
it in the app switcher and reopen it, twice if the first time is still blank, and it picks up
the new version by itself.

**https://kanishk-gangwar.github.io/Splits/app/**


## A new launch animation

Splits now opens with its icon acting out the name: the ring appears, the bar drops through
it, the two halves slide apart and "Splits" fades in, then it fades into your groups. On
Android and on iPhone, in light and dark mode.


## Also fixed

- **The web app no longer gets stuck on the splash after an update.** A phone could hold on
  to part of the old version for up to 10 minutes after a new one went live.
- **No more red "Script error." box on iPhone** when you open Safari's share sheet or page
  menu. That error came from Safari itself, not from Splits.
- **If something does go wrong in the web app**, it now shows the error on screen in place of
  a blank page, so you can send a screenshot of it.
