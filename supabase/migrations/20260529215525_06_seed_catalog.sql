-- Seeds (Figure 9). Field Daisy + Sunset Tulip are starters/free; the rest cost coins.
-- Bright Poppy is locked behind level 5.
insert into public.items (slug, category, name, description, rarity, price_coins, level_required, asset_key, is_purchasable, is_starter) values
  ('seed.field_daisy',    'seed', 'Field Daisy',    'A cheerful daisy to start your garden.',     'common',     0,   1, 'seed_field_daisy',    false, true),
  ('seed.sunset_tulip',   'seed', 'Sunset Tulip',   'A warm tulip with sunset hues.',              'common',     0,   1, 'seed_sunset_tulip',   false, true),
  ('seed.big_sunflower',  'seed', 'Big Sunflower',  'A towering sunflower that follows the sun.', 'common',    60,   1, 'seed_big_sunflower',  true,  false),
  ('seed.sleepy_lavender','seed', 'Sleepy Lavender','A calming lavender plant.',                  'uncommon',  80,   2, 'seed_sleepy_lavender',true,  false),
  ('seed.wild_rose',      'seed', 'Wild Rose',      'A vibrant rose with thorns and beauty.',     'rare',     120,   3, 'seed_wild_rose',      true,  false),
  ('seed.bright_poppy',   'seed', 'Bright Poppy',   'A bold poppy that catches every eye.',       'epic',     180,   5, 'seed_bright_poppy',   true,  false);

-- Decor (Figure 10). Terracotta Pot is a starter; Bird Bath is locked behind level 4.
insert into public.items (slug, category, name, description, rarity, price_coins, level_required, asset_key, is_purchasable, is_starter) values
  ('decor.terracotta_pot','decor', 'Terracotta Pot','A classic terracotta planter.',              'common',     0,   1, 'decor_terracotta_pot',false, true),
  ('decor.picket_fence',  'decor', 'Picket Fence',  'A tidy white picket fence segment.',         'common',    50,   1, 'decor_picket_fence',  true,  false),
  ('decor.garden_bench',  'decor', 'Garden Bench',  'A cozy garden bench to sit and reflect.',    'uncommon',  80,   2, 'decor_garden_bench',  true,  false),
  ('decor.stone_lantern', 'decor', 'Stone Lantern', 'A traditional stone lantern.',               'uncommon', 110,   2, 'decor_stone_lantern', true,  false),
  ('decor.toadstool_pair','decor', 'Toadstool Pair','A pair of red-capped toadstools.',           'rare',     140,   3, 'decor_toadstool_pair',true,  false),
  ('decor.bird_bath',     'decor', 'Bird Bath',     'A stone bird bath that attracts visitors.',  'epic',     220,   4, 'decor_bird_bath',     true,  false);

-- Backdrops (Figure 11). Cottage Meadow is the starter active backdrop.
-- Cherry Grove is a limited-time item (1-week window from now as an example).
insert into public.items (slug, category, name, description, rarity, price_coins, level_required, asset_key, is_purchasable, is_starter, available_from, available_until) values
  ('backdrop.cottage_meadow','backdrop','Cottage Meadow','Gentle hills and a quiet cottage.',          'common',     0, 1, 'backdrop_cottage_meadow',false, true,  null,                                  null),
  ('backdrop.misty_forest',  'backdrop','Misty Forest',  'Tall pines wrapped in cool mist.',           'uncommon', 160, 2, 'backdrop_misty_forest',  true,  false, null,                                  null),
  ('backdrop.cherry_grove',  'backdrop','Cherry Grove',  'Soft pinks and gentle petals.',              'rare',     180, 3, 'backdrop_cherry_grove',  true,  false, now(),                                 now() + interval '7 days'),
  ('backdrop.quiet_shore',   'backdrop','Quiet Shore',   'A calm coastline at golden hour.',           'rare',     200, 4, 'backdrop_quiet_shore',   true,  false, null,                                  null),
  ('backdrop.desert_sunset', 'backdrop','Desert Sunset', 'Warm dunes under golden light.',             'epic',     220, 5, 'backdrop_desert_sunset', true,  false, null,                                  null);

-- Bundles (Figures 9, 10).
insert into public.bundles (slug, name, description, price_coins, is_featured) values
  ('bundle.wildflower',   'Wildflower Bundle',  '4 rare seeds at 25% off.',          240, true),
  ('bundle.cottage_starter','Cottage Starter Set','Fence + lantern + bench combo.',  180, true);

insert into public.bundle_items (bundle_id, item_id)
select (select id from public.bundles where slug = 'bundle.wildflower'), id
from public.items
where slug in ('seed.sleepy_lavender','seed.wild_rose','seed.bright_poppy','seed.big_sunflower');

insert into public.bundle_items (bundle_id, item_id)
select (select id from public.bundles where slug = 'bundle.cottage_starter'), id
from public.items
where slug in ('decor.picket_fence','decor.stone_lantern','decor.garden_bench');

-- Achievements (Figure 13: First Sprout, 7-Day Streak, Self-Kind, 30 Days).
insert into public.achievements (slug, name, description, icon_key, criteria) values
  ('first_sprout',  'First Sprout',  'Plant your very first seed.',           'achv_first_sprout',  '{"type":"plants_grown","value":1}'::jsonb),
  ('streak_7',      '7-Day Streak',  'Submit gratitude entries 7 days in a row.','achv_streak_7',    '{"type":"streak","value":7}'::jsonb),
  ('self_kind',     'Self-Kind',     'Log 10 self-compassion entries.',       'achv_self_kind',     '{"type":"entries_tagged","tag":"self","value":10}'::jsonb),
  ('streak_30',     '30 Days',       'Reach a 30-day gratitude streak.',      'achv_streak_30',     '{"type":"streak","value":30}'::jsonb),
  ('thoughts_100',  'Century of Kindness','Plant 100 kind thoughts.',         'achv_thoughts_100',  '{"type":"total_entries","value":100}'::jsonb),
  ('garden_full',   'Garden in Bloom','Grow 10 plants to mature.',            'achv_garden_full',   '{"type":"plants_grown","value":10}'::jsonb);
