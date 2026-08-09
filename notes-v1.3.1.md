## Fixes invite links — update if you are on 1.3.0

**1.3.0 could not open invite links.** Codes became 12 characters in that release but the link
parser still only accepted 8, so tapping a freshly shared invite did nothing at all — no error,
just nothing. Any link shared from 1.3.0 works again once both people are on 1.3.1.

Links shared by older versions were never affected, and no data was lost either way.

### Also in this build

- **Share the app from the home screen.** A share button in the top bar, next to Join and
  Settings, sends the download link to whoever you like.
- Links are now recognised inside a whole forwarded message, not just as a bare URL — which is
  how invites actually arrive.
- A stray URL can no longer be mistaken for an invite code.

### Still required, if you have not done it

The security fix in 1.3.0 needs `backend/supabase/schema.sql` applied to your Supabase project.
Without it the app cannot sync at all. See *Server security model* in TECHNICAL.md for what it
closes.
