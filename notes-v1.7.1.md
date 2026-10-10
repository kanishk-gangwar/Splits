## Bug fix: you can see entries again

1.7.0 made expenses and settlements editable only by whoever added them, or by the group
admin. It went one step too far: everyone else could not even open those entries. If someone
added a dinner split with you, it counted in your balance but tapping it did nothing.

Now every entry opens. If you added it, or you are the group admin, you get the editor as
before. Anyone else gets a details page: the amount, the date, who paid, how it was split and
what your share was, and the note — with no Save or Delete, and a line saying who added it.

Entries in archived groups open the same way. Before, they could not be opened at all.

No Supabase changes in this build.
