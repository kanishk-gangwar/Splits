## The app can tell you it is out of date

Splits is installed from an APK, not from a store, so nothing has ever told anyone that a new
version exists. You found out because somebody mentioned it. That is a poor way to ship a fix —
1.5.0 corrected a bug that quietly doubled a settlement, and a release nobody hears about fixes
nothing.

**Settings ends with an App version card.** It shows what you are running, checks GitHub for the
newest release, and gives you the download when there is one. Tapping it opens the APK in your
browser; Android asks whether to install it, and your groups, expenses and identity are kept —
it is an update over the top, not a fresh install.

A few deliberate choices in there:

- **If the check cannot reach GitHub, it says so and still offers the download.** "Could not
  check" is not the same as "you are up to date", and pretending otherwise is how people sit on
  an old build believing they are current.
- **The app does not download or install anything itself.** It hands the link to your browser
  and the install prompt is Android's, exactly as when you first installed it. An app that could
  install other apps needs a permission it has no other honest use for.
- **The version check reads one number and nothing else.** It asks the public releases endpoint
  with no token attached, uses the answer only to decide whether to say an update is out, and
  always opens the download link built into the app rather than any address the reply carries.

## Also

Nothing else changed. Everything from 1.5.0 — one settlement per payment, and the admin being
able to free a name — is unchanged, and this build needs no Supabase changes beyond the schema
re-run 1.5.0 already asked for.
