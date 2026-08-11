## Typing an invite code works again

1.3.1 fixed invite links by teaching the parser that codes are 12 characters now. Two more
places had their own idea of a valid code, and both were missed:

**The join dialog demanded exactly 8 characters.** Codes have been 12 since they were
lengthened, so nothing you typed could enable Continue. The dialog now takes its idea of a
valid code from the same place as the link parser — 12 characters, or 8 from an older invite —
and it forgives how the code was copied: spaced in groups of four the way the app displays it,
lowercase from a keyboard, or the whole invite link pasted instead of just the code.

**The invite web page rejected every fresh link.** The page a shared link opens in a browser
still checked for 8 characters too, so it told everyone the link "looks incomplete". It now
accepts both lengths and shows 12-character codes in readable groups of four. That page is
served straight from `main`, so it is fixed the moment this release lands — no update needed
for the person clicking the link, only for the one typing a code into the app.

Older 8-character codes and links keep working everywhere, as before.
