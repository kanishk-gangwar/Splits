## Fix: 1.7.0 could not sync or join groups

1.7.0 was built without its server connection. It looked normal, but it never synced: new
expenses stayed on your phone, nobody else's changes arrived, and invite links said the group
did not exist. This version reconnects. Anything you added on 1.7.0 is still on your phone and
goes up on the first sync.

**If your name now shows as taken.** 1.7.0 also switched your phone to its new, reinstall-proof
id without telling the server, so the server may still hold your name under the old one. After
updating, open the group: if it asks which one you are and your name is taken, ask the group
admin to free it in group settings, then pick it again. If the admin is stuck too, the group
owner can free every name in one go.

## Also fixed

**Entries other people added open again.** 1.7.0 made expenses and settlements editable only
by whoever added them, or by the admin — but everyone else could not even open them. Now they
open as a details page: amount, date, who paid, how it was split, the note, and who added it,
with no Save or Delete. Entries in archived groups open the same way.
