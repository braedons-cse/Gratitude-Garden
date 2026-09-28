-- The backdrop ladder (roadmap 1.3). The app is about to show and equip backdrops, which
-- makes them the visible reward for levelling: one backdrop at each of levels 2 to 5.
-- Levels 2, 4 and 5 are right in the live catalog; level 3 has nothing.
--
--   * Live data differs from 06_seed_catalog. Cherry Grove was seeded at level 3 with a
--     one-week sale window (now() .. now() + 7 days at seed time), and was edited to level 1
--     since. The window closed on 2026-06-05, so purchase_item refuses it with "item no
--     longer available" and no one has ever owned it.
--
--   * Made permanent rather than seasonal. The app doesn't mirror available_from/until
--     yet, so an expired item would show a price the server then refuses. Seasonal items
--     can bring the window back once the shop knows how to hide them.
--
-- No function changes: set_active_backdrop already checks ownership and category, and
-- purchase_item already checks the level. Nobody owns Cherry Grove, so no one is affected.

update public.items
   set level_required = 3,
       available_from = null,
       available_until = null
 where slug = 'backdrop.cherry_grove';

notify pgrst, 'reload schema';
